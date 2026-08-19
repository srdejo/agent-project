# =========================
# CONFIGURACION
# =========================
$VPS_USER = "srdejo"
$VPS_HOST = "nolost-vps"

$BACKEND_PATH = ".\backend"
$FRONTEND_PATH = ".\frontend"

$REMOTE_BACKEND_DIR = "/home/$VPS_USER/agent-project"
$REMOTE_FRONTEND_DIR = "/home/$VPS_USER/agent-project-frontend"

# =========================
# FUNCIONES
# =========================

function Build-Backend {
    Write-Host "[BUILD] Construyendo backend..."
    Push-Location $BACKEND_PATH
    .\gradlew.bat clean bootJar -x test
    Pop-Location
}

function Deploy-Backend {
    Build-Backend

    Push-Location "$BACKEND_PATH\bootstrap"
    $JAR_NAME = Get-ChildItem "build\libs\*.jar" | Where-Object { $_.Name -notmatch '-plain\.jar$' } | Sort-Object LastWriteTime | Select-Object -Last 1
    $JAR_BASENAME = $JAR_NAME.Name
    $JAR_FULL = $JAR_NAME.FullName
    Pop-Location

    Write-Host "[DEPLOY] Creando carpetas remotas (si no existen)..."
    # data/ e inbox/ nunca se borran ni se sobrescriben aqui — es donde vive el estado del servicio.
    ssh "${VPS_USER}@${VPS_HOST}" "mkdir -p $REMOTE_BACKEND_DIR/data/inbox"

    Write-Host "[DEPLOY] Subiendo JAR al servidor..."
    scp "$JAR_FULL" "${VPS_USER}@${VPS_HOST}:${REMOTE_BACKEND_DIR}/"

    Write-Host "[DEPLOY] Creando enlace simbolico y reiniciando servicio..."
    ssh "${VPS_USER}@${VPS_HOST}" "rm -f $REMOTE_BACKEND_DIR/app.jar && ln -s $REMOTE_BACKEND_DIR/$JAR_BASENAME $REMOTE_BACKEND_DIR/app.jar"
    Restart-Backend

    Write-Host "[OK] Backend desplegado correctamente."
}

function Restart-Backend {
    Write-Host "[RESTART] Reiniciando servicio backend (agent-project)..."
    ssh "${VPS_USER}@${VPS_HOST}" "sudo systemctl restart agent-project"
    Write-Host "[OK] Backend reiniciado. Flyway aplica migraciones pendientes automaticamente al arrancar."
}

function Build-Frontend {
    Write-Host "[BUILD] Construyendo frontend..."
    Push-Location $FRONTEND_PATH
    ng build --configuration production
    Pop-Location
}

function Deploy-Frontend {
    Build-Frontend

    Write-Host "[DEPLOY] Limpiando carpeta remota..."
    ssh "${VPS_USER}@${VPS_HOST}" "mkdir -p $REMOTE_FRONTEND_DIR && rm -rf $REMOTE_FRONTEND_DIR/*"

    Write-Host "[DEPLOY] Desplegando frontend..."
    scp -r "$FRONTEND_PATH\dist\frontend\browser\*" "${VPS_USER}@${VPS_HOST}:${REMOTE_FRONTEND_DIR}/"
    Restart-Frontend

    Write-Host "[OK] Frontend desplegado correctamente."
}

function Restart-Frontend {
    Write-Host "[RESTART] Reiniciando nginx (frontend)..."
    ssh "${VPS_USER}@${VPS_HOST}" "sudo systemctl restart nginx"
    Write-Host "[OK] nginx reiniciado."
}

function Backup-Database {
    Write-Host "[BACKUP] Generando backup..."

    $DATE = Get-Date -Format "yyyyMMdd_HHmmss"

    ssh "${VPS_USER}@${VPS_HOST}" "pg_dump -U agent_project agent_project | gzip > /home/$VPS_USER/backups/agent_project_db_backup_$DATE.sql.gz"

    Write-Host "[BACKUP] Descargando backup a local..."

    if (-not (Test-Path ".\backups")) {
        New-Item -ItemType Directory -Path ".\backups" | Out-Null
    }

    scp "${VPS_USER}@${VPS_HOST}:/home/$VPS_USER/backups/agent_project_db_backup_$DATE.sql.gz" ".\backups\"

    Write-Host "[OK] Backup guardado en VPS y en tu maquina."
}

function View-Logs {
    Write-Host "[LOGS] Mostrando logs en tiempo real (Ctrl+C para salir)..."
    ssh "${VPS_USER}@${VPS_HOST}" "journalctl -u agent-project -f --no-pager"
}

function Deploy-All {
    Build-Backend
    Deploy-Backend
    Build-Frontend
    Deploy-Frontend
}

# =========================
# MENU
# =========================

while ($true) {
    Write-Host ""
    Write-Host "========= DEPLOY AGENT-PROJECT ========="
    Write-Host "1) Build Backend"
    Write-Host "2) Deploy Backend"
    Write-Host "3) Build Frontend"
    Write-Host "4) Deploy Frontend"
    Write-Host "5) Deploy Todo (Backend + Frontend)"
    Write-Host "6) Reiniciar Backend (servicio agent-project)"
    Write-Host "7) Reiniciar Frontend (nginx)"
    Write-Host "8) Backup Database"
    Write-Host "9) Ver Logs"
    Write-Host "10) Salir"
    Write-Host "========================================="

    $input = Read-Host "Selecciona una o varias opciones (ej: 1,2,8)"
    $opciones = $input -split '[,\s]+' | Where-Object { $_ -ne "" }

    foreach ($opcion in $opciones) {
        switch ($opcion) {
            "1"  { Build-Backend }
            "2"  { Deploy-Backend }
            "3"  { Build-Frontend }
            "4"  { Deploy-Frontend }
            "5"  { Deploy-All }
            "6"  { Restart-Backend }
            "7"  { Restart-Frontend }
            "8"  { Backup-Database }
            "9"  { View-Logs }
            "10" { Write-Host "Saliendo..."; exit 0 }
            default { Write-Host "[ERROR] Opcion invalida: $opcion" }
        }
    }
}
