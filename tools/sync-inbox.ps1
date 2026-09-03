<#
.SYNOPSIS
    Sube progreso.json y nuevo.json al inbox de agent-project en el VPS, de forma atomica.

.DESCRIPTION
    Implementa el contrato de docs/SYNC_PROTOCOL.md:
      1. Comprueba que el backend esta vivo antes de subir nada.
      2. Consulta GET /api/projects y CLASIFICA cada proyecto en el archivo que le
         corresponde: los ids que ya existen van a progreso.json, los que no a
         nuevo.json. El backend descarta en silencio las entradas mal clasificadas,
         asi que el script las reubica solo. No es una decision: el API es la
         verdad, y el script la aplica.
      3. Valida lo que SI requiere criterio (enums, rangos, campos requeridos) y
         se niega a subir si algo esta mal.
      4. Sube cada archivo como .tmp y lo renombra con mv en el servidor.
      5. Espera el debounce y verifica contra el API que el sync se aplico.

    NOTA: este archivo se mantiene en ASCII puro a proposito. Windows PowerShell 5.1
    lee los .ps1 sin BOM como ANSI, y cualquier caracter no-ASCII (tildes, guiones
    largos) rompe el parseo con errores enganosos de "string is missing the terminator".

.EXAMPLE
    .\sync-inbox.ps1
    .\sync-inbox.ps1 -DryRun          # valida, clasifica y lista los bloqueos, sin subir
    .\sync-inbox.ps1 -Bump            # fuerza la reaplicacion aunque no cambie last_modified
    .\sync-inbox.ps1 -NoBlockers      # omite el listado de tareas bloqueadas
    .\sync-inbox.ps1 -NoReclasificar  # no reubica: falla si algun id esta en el archivo equivocado
#>

[CmdletBinding()]
param(
    [string] $VpsHost     = "nolost-vps",
    [string] $InboxDir    = "/home/srdejo/agent-project/data/inbox",
    [string] $ApiBase     = "https://agent.srdejo.com.co",
    [int]    $BackendPort = 8083,
    [string] $Progreso    = "$PSScriptRoot\progreso.json",
    [string] $Nuevo       = "$PSScriptRoot\nuevo.json",
    [switch] $DryRun,
    [switch] $SkipVerify,
    [switch] $NoBlockers,
    [switch] $Bump,
    [switch] $NoReclasificar
)

$ErrorActionPreference = "Stop"

$PROJ_STATUS = @("IN_PROGRESS", "BLOCKED", "STARTED", "COMPLETED")
$VERIFY      = @("PASSED", "ATTENTION", "PENDING")
$TASK_STATUS = @("done", "wip", "blocked", "todo")
$PRIORITIES  = @("NOW", "NEXT", "DECIDE", "ON_TRACK", "FROZEN")
$ALIAS_MAX   = 120

function Write-Step($msg) { Write-Host ""; Write-Host ">> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "   OK  $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "   !   $msg" -ForegroundColor Yellow }
function Write-Bad($msg)  { Write-Host "   X   $msg" -ForegroundColor Red }

# Lee GET /api/projects. Si el sitio esta detras de oauth2-proxy, la peticion
# publica devuelve el login en vez de JSON; en ese caso repite la consulta por
# dentro del VPS via SSH contra el puerto loopback del backend. Reusa la
# autenticacion SSH que ya existe, sin abrir huecos en nginx ni inventar tokens.
function Get-Projects {
    try {
        $r = Invoke-WebRequest -Uri "$ApiBase/api/projects" -Method Get -TimeoutSec 20 `
                               -UseBasicParsing -MaximumRedirection 0 -ErrorAction Stop
        $datos = $r.Content | ConvertFrom-Json -ErrorAction Stop
        if ($null -ne $datos.projects) {
            return @{ Origen = "publico"; Datos = $datos }
        }
    } catch {
        # 302/401/403, o HTML de login en vez de JSON: no es un fallo, es la puerta.
    }

    Write-Warn "El API publico no devolvio JSON (parece protegido por login). Consultando por SSH."
    $json = ssh $VpsHost "curl -s --max-time 15 http://127.0.0.1:$BackendPort/api/projects"
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($json)) {
        throw "Tampoco respondio por SSH en 127.0.0.1:$BackendPort. Revisa: ssh $VpsHost 'systemctl is-active agent-project'"
    }
    return @{ Origen = "ssh"; Datos = ($json | ConvertFrom-Json) }
}

function Read-Mapa($Ruta) {
    if (-not (Test-Path $Ruta)) { return @{} }
    $texto = Get-Content $Ruta -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($texto)) { return @{} }
    $obj = $texto | ConvertFrom-Json
    $mapa = [ordered]@{}
    foreach ($prop in $obj.PSObject.Properties) { $mapa[$prop.Name] = $prop.Value }
    return $mapa
}

function Write-Mapa($Mapa, $Ruta) {
    # -Encoding UTF8 en Windows PowerShell 5.1 SIEMPRE antepone un BOM. Jackson no
    # lo ignora al leer un String (solo al detectar encoding desde un stream), asi
    # que el backend rechaza el archivo entero como JSON invalido y lo borra sin
    # aplicar nada, aunque el log parezca exitoso. Por eso se escribe a mano con
    # UTF8Encoding($false) (sin BOM).
    $obj = [pscustomobject]$Mapa
    $json = $obj | ConvertTo-Json -Depth 12
    [System.IO.File]::WriteAllText($Ruta, $json, (New-Object System.Text.UTF8Encoding($false)))
}

# --- 1. esta vivo el backend? --------------------------------------------------
Write-Step "Comprobando que el backend responde"
try {
    $lectura = Get-Projects
    Write-Ok "API leido ($($lectura.Origen))"
} catch {
    Write-Bad "El backend no responde: $_"
    exit 1
}

$existentes = @( $lectura.Datos.projects | ForEach-Object { $_.id } )
Write-Ok "$($existentes.Count) proyectos ya en la base"

# --- 2. clasificar cada proyecto en el archivo que le toca ---------------------
Write-Step "Clasificando proyectos segun lo que dice el API"

$origenProgreso = Read-Mapa $Progreso
$origenNuevo    = Read-Mapa $Nuevo

$todos = [ordered]@{}
foreach ($k in $origenProgreso.Keys) { $todos[$k] = $origenProgreso[$k] }
foreach ($k in $origenNuevo.Keys) {
    if ($todos.Contains($k)) {
        Write-Warn "${k}: aparece en los DOS archivos. Me quedo con la entrada de nuevo.json."
    }
    $todos[$k] = $origenNuevo[$k]
}

if ($todos.Count -eq 0) {
    Write-Bad "No hay ningun proyecto en progreso.json ni en nuevo.json."
    exit 1
}

$destProgreso = [ordered]@{}
$destNuevo    = [ordered]@{}
$movidos      = New-Object System.Collections.ArrayList

foreach ($id in $todos.Keys) {
    $existe = $existentes -contains $id
    $estabaEn = if ($origenNuevo.Contains($id)) { "nuevo.json" } else { "progreso.json" }
    $vaA      = if ($existe) { "progreso.json" } else { "nuevo.json" }

    if ($existe) { $destProgreso[$id] = $todos[$id] } else { $destNuevo[$id] = $todos[$id] }

    if ($estabaEn -ne $vaA) {
        $razon = if ($existe) { "ya existe en la base" } else { "todavia no existe en la base" }
        [void]$movidos.Add("${id}: $estabaEn -> $vaA ($razon)")
    }
}

if ($movidos.Count -gt 0) {
    if ($NoReclasificar) {
        Write-Bad "Hay $($movidos.Count) proyecto(s) en el archivo equivocado y -NoReclasificar esta activo:"
        foreach ($m in $movidos) { Write-Host "       - $m" -ForegroundColor Red }
        exit 1
    }
    Write-Warn "Reubicando $($movidos.Count) proyecto(s):"
    foreach ($m in $movidos) { Write-Host "       $m" -ForegroundColor Yellow }
} else {
    Write-Ok "Cada proyecto ya estaba en su archivo"
}

Write-Ok "progreso.json: $($destProgreso.Count) | nuevo.json: $($destNuevo.Count)"

# --- 3. validar lo que SI requiere criterio ------------------------------------
Write-Step "Validando esquema"
$problemas    = New-Object System.Collections.ArrayList
$bloqueadas   = New-Object System.Collections.ArrayList
$sinPrioridad = New-Object System.Collections.ArrayList

foreach ($id in $todos.Keys) {
    $p = $todos[$id]

    if ($null -eq $p.progress -or $p.progress -lt 0 -or $p.progress -gt 100) {
        [void]$problemas.Add("${id}: progress fuera de 0-100 (valor: $($p.progress))")
    }
    if ($PROJ_STATUS -notcontains $p.status) {
        [void]$problemas.Add("${id}: status de proyecto invalido '$($p.status)'")
    }
    if ($VERIFY -notcontains $p.verify) {
        [void]$problemas.Add("${id}: verify invalido '$($p.verify)'")
    }
    foreach ($campo in @("name", "repo", "last_modified")) {
        if ([string]::IsNullOrWhiteSpace($p.$campo)) {
            [void]$problemas.Add("${id}: falta el campo requerido '$campo'")
        }
    }
    foreach ($t in $p.tasks) {
        if ($TASK_STATUS -cnotcontains $t.status) {
            $nombreTarea = $t.name
            [void]$problemas.Add("${id}: tarea '$nombreTarea' tiene status '$($t.status)'. Debe ir en minusculas (done|wip|blocked|todo) o el backend descarta el proyecto entero.")
        }
    }

    # Capa editorial: opcional, pero si viene tiene que ser valida.
    if ($null -ne $p.priority -and $p.priority -ne "" -and $PRIORITIES -notcontains $p.priority) {
        [void]$problemas.Add("${id}: priority '$($p.priority)' invalida. Debe ser NOW, NEXT, DECIDE, ON_TRACK o FROZEN.")
    }
    if ($null -ne $p.priority_rank -and $p.priority_rank -ne "" -and [int]$p.priority_rank -lt 1) {
        [void]$problemas.Add("${id}: priority_rank '$($p.priority_rank)' invalido. Debe ser un entero >= 1.")
    }
    if ($p.alias -and $p.alias.Length -gt $ALIAS_MAX) {
        [void]$problemas.Add("${id}: alias de $($p.alias.Length) caracteres, el maximo es $ALIAS_MAX.")
    }
    if ($null -eq $p.priority -or $p.priority -eq "") { [void]$sinPrioridad.Add($id) }

    $blk = @($p.tasks | Where-Object { $_.status -ceq "blocked" })
    foreach ($t in $blk) {
        [void]$bloqueadas.Add([pscustomobject]@{ Proyecto = $id; Etapa = $t.stage; Tarea = $t.name })
    }

    $done = @($p.tasks | Where-Object { $_.status -ceq "done" }).Count
    $wip  = @($p.tasks | Where-Object { $_.status -ceq "wip" }).Count
    $todo = @($p.tasks | Where-Object { $_.status -ceq "todo" }).Count
    $rk   = if ($p.priority_rank) { $p.priority_rank } else { "-" }
    $prio = if ($p.priority) { $p.priority } else { "-" }
    $arch = if ($destProgreso.Contains($id)) { "progreso" } else { "NUEVO" }

    $linea = "       {0,-3} {1,-18} {2,-9} {3,3}%  {4,3}/{5,-3} verif   wip {6,2}   todo {7,2}   blk {8,2}   {9}" -f `
             $rk, $id, $prio, $p.progress, $done, @($p.tasks).Count, $wip, $todo, $blk.Count, $arch
    if ($blk.Count -gt 0) { Write-Host $linea -ForegroundColor Yellow } else { Write-Host $linea }
}

if ($problemas.Count -gt 0) {
    Write-Host ""
    Write-Bad "Se encontraron $($problemas.Count) problema(s) que el script NO puede decidir por ti. No se sube nada:"
    foreach ($x in $problemas) { Write-Host "       - $x" -ForegroundColor Red }
    exit 1
}
Write-Ok "Esquema correcto"

if ($sinPrioridad.Count -gt 0) {
    Write-Warn "$($sinPrioridad.Count) proyecto(s) sin priority: $($sinPrioridad -join ', ')"
}

# --- 4. lo que va a quedar bloqueado en el dashboard ---------------------------
if (-not $NoBlockers -and $bloqueadas.Count -gt 0) {
    Write-Step "$($bloqueadas.Count) tarea(s) bloqueada(s) - esto es lo que el dashboard va a marcar como BLOCKED"
    Write-Host "   Recordatorio: 'blocked' incluye la falta de definicion, no solo el impedimento externo." -ForegroundColor DarkGray
    Write-Host ""
    foreach ($grupo in ($bloqueadas | Group-Object -Property Proyecto | Sort-Object -Property Count -Descending)) {
        Write-Host ("   {0} ({1})" -f $grupo.Name, $grupo.Count) -ForegroundColor Yellow
        foreach ($b in $grupo.Group) {
            $etapa = $b.Etapa
            if ([string]::IsNullOrWhiteSpace($etapa)) { $etapa = "-" }
            if ($etapa.Length -gt 26) { $etapa = $etapa.Substring(0, 25) + "." }
            Write-Host ("       {0,-26}  {1}" -f $etapa, $b.Tarea)
        }
        Write-Host ""
    }
}

# --- 5. materializar los archivos que se van a subir ---------------------------
# La reclasificacion se escribe tambien en el repo: progreso.json y nuevo.json son
# artefactos generados, y su reparto es objetivamente derivable del API. Dejarlos
# mal repartidos solo haria repetir el mismo aviso en cada corrida.
if ($movidos.Count -gt 0 -and -not $DryRun) {
    Write-Mapa $destProgreso $Progreso
    Write-Mapa $destNuevo    $Nuevo
    Write-Ok "progreso.json y nuevo.json reescritos con la clasificacion correcta"
}

$rutaProgreso = $Progreso
$rutaNuevo    = $Nuevo

if ($Bump) {
    # El backend compara 'last_modified' y responde UNCHANGED si es igual. Eso
    # bloquea el caso en que cambia el CONTRATO y no el estado: campos nuevos que
    # hay que poblar sobre proyectos ya sincronizados. -Bump reescribe la fecha
    # solo en las copias temporales que se suben.
    Write-Step "-Bump: reescribiendo last_modified para forzar la reaplicacion"
    $ahora = (Get-Date).ToString("yyyy-MM-ddTHH:mm:sszzz")
    foreach ($id in $destProgreso.Keys) { $destProgreso[$id].last_modified = $ahora }
    foreach ($id in $destNuevo.Keys)    { $destNuevo[$id].last_modified    = $ahora }
    Write-Ok "$($todos.Count) proyecto(s) con last_modified = $ahora (solo en la copia que se sube)"
}

$tmpDir = [System.IO.Path]::GetTempPath()
$rutaProgreso = Join-Path $tmpDir "sync-progreso.json"
$rutaNuevo    = Join-Path $tmpDir "sync-nuevo.json"
Write-Mapa $destProgreso $rutaProgreso
Write-Mapa $destNuevo    $rutaNuevo

if ($DryRun) {
    Write-Host ""
    Write-Host "-DryRun activo: no se subio nada." -ForegroundColor Yellow
    if ($movidos.Count -gt 0) {
        Write-Host "Sin -DryRun, ademas se reescribirian progreso.json y nuevo.json con la clasificacion correcta." -ForegroundColor Yellow
    }
    exit 0
}

# --- 6. subida atomica ---------------------------------------------------------
Write-Step "Subiendo al inbox de $VpsHost"

function Send-Atomic {
    param([string] $Ruta, [string] $Nombre, [int] $Cuantos)

    if ($Cuantos -eq 0) {
        Write-Ok "$Nombre : sin proyectos, no se sube"
        return
    }
    $destino = "$InboxDir/$Nombre"

    scp $Ruta "${VpsHost}:${destino}.tmp"
    if ($LASTEXITCODE -ne 0) { throw "scp de $Nombre fallo con codigo $LASTEXITCODE" }

    ssh $VpsHost "mv '${destino}.tmp' '${destino}'"
    if ($LASTEXITCODE -ne 0) { throw "mv de $Nombre fallo con codigo $LASTEXITCODE" }

    Write-Ok "$Nombre subido y renombrado ($Cuantos proyecto(s))"
}

Send-Atomic -Ruta $rutaProgreso -Nombre "progreso.json" -Cuantos $destProgreso.Count
Send-Atomic -Ruta $rutaNuevo    -Nombre "nuevo.json"    -Cuantos $destNuevo.Count

if ($SkipVerify) {
    Write-Host ""
    Write-Host "Listo (verificacion omitida)." -ForegroundColor Green
    exit 0
}

# --- 7. verificar que el backend lo aplico -------------------------------------
Write-Step "Esperando a que el WatchService procese el inbox"
Start-Sleep -Seconds 5

$restantes = ssh $VpsHost "ls -1 $InboxDir 2>/dev/null | grep -c json"
if ($restantes -and [int]$restantes -gt 0) {
    Write-Warn "Quedan $restantes archivo(s) json en el inbox. El backend los borra al terminar; reintenta en unos segundos."
} else {
    Write-Ok "Inbox vacio: el backend proceso y borro los archivos"
}

$final = (Get-Projects).Datos

Write-Host ""
Write-Host ("{0,-4}{1,-18}{2,-10}{3,6}  {4}" -f "#", "proyecto", "grupo", "prog", "etapa")
Write-Host ("-" * 90)
foreach ($p in ($final.projects | Sort-Object -Property @{ Expression = { if ($_.priorityRank) { $_.priorityRank } else { 999 } } })) {
    $etapa = ""
    if ($p.stage) { $etapa = $p.stage.Substring(0, [Math]::Min(44, $p.stage.Length)) }
    $rk = if ($p.priorityRank) { $p.priorityRank } else { "-" }
    $gr = if ($p.priority) { $p.priority } else { "-" }
    Write-Host ("{0,-4}{1,-18}{2,-10}{3,5}%  {4}" -f $rk, $p.id, $gr, $p.progress, $etapa)
}

if ($null -eq ($final.projects | Select-Object -First 1).priority) {
    Write-Host ""
    Write-Warn "El API no esta devolviendo 'priority'. El backend con las migraciones V5/V6 todavia no esta desplegado."
}

$faltantes = @($destNuevo.Keys | Where-Object { $final.projects.id -notcontains $_ })
if ($faltantes.Count -gt 0) {
    Write-Host ""
    Write-Warn "Estos proyectos de nuevo.json NO aparecen en el API: $($faltantes -join ', ')"
    Write-Warn "Revisa los logs:  ssh $VpsHost 'journalctl -u agent-project -n 60 --no-pager'"
    exit 1
}

Write-Host ""
Write-Host "Sync aplicado correctamente." -ForegroundColor Green
