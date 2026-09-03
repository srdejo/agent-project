# tools/ — generación y envío del sync

Implementa lo que `docs/SYNC_PROTOCOL.md` deja explícitamente fuera de alcance: **cómo se
construyen** `progreso.json` y `nuevo.json` a partir de los repos, y cómo se suben al inbox.

## Qué hay aquí

| Archivo | Qué es |
|---|---|
| `progreso.json` | Actualización para los 9 proyectos que ya existen en la base. |
| `nuevo.json` | Alta de `micasachurch`, el único proyecto del workspace que todavía no estaba en el dashboard. |
| `sync-inbox.ps1` | Sube ambos archivos al VPS de forma atómica y verifica que el backend los aplicó. |

## Cómo usarlo

```powershell
cd D:\Workspace Daniel\01-activos\srdejo\agent-project\tools
.\sync-inbox.ps1 -DryRun       # valida y lista los bloqueos, sin subir
.\sync-inbox.ps1               # valida, sube y verifica
.\sync-inbox.ps1 -Bump         # fuerza la reaplicacion (ver abajo)
.\sync-inbox.ps1 -NoBlockers   # omite el listado de bloqueos
```

### `-Bump`: cuando cambia el contrato y no el estado

El backend compara `last_modified` contra lo guardado y, si es igual, responde `UNCHANGED` y no
toca nada. Eso es correcto casi siempre — evita reescribir la base y ensuciar `project_snapshots`
con instantáneas idénticas.

Pero bloquea un caso real: **agregaste campos nuevos al contrato y necesitas poblarlos sobre
proyectos que ya se sincronizaron**. El estado no cambió, así que `last_modified` tampoco, así que
el backend ignora el archivo y los campos nuevos nunca llegan. Se ve como si el sync hubiera
funcionado, y la única señal es que el dato sigue en `null`.

`-Bump` reescribe `last_modified` al instante actual **solo en las copias temporales que se suben**;
los archivos del repo no se tocan, así que no se pierde la fecha real del estado descrito. Úsalo
justo después de desplegar un backend con campos nuevos, y no de rutina.

El script hace cuatro cosas, en este orden:

1. **Comprueba que el backend está vivo** (`GET /api/projects` → 200). Si no responde, no sube nada
   y te dice qué revisar en systemd.
2. **Valida la clasificación contra el API**, no contra memoria: cada id de `progreso.json` tiene que
   existir ya, y cada id de `nuevo.json` no. El backend descarta en silencio las entradas mal
   clasificadas, así que el error se detecta antes de subir, no después. También valida enums,
   rangos y —lo más fácil de romper— que ningún `tasks[].status` venga en mayúsculas.
3. **Sube de forma atómica**: `scp` a `.tmp` y `mv` en el servidor, para que el `WatchService`
   nunca lea un archivo a medio escribir.
4. **Verifica**: espera el debounce, confirma que el inbox quedó vacío (el backend borra los
   archivos al terminar) y vuelve a consultar el API para mostrar el estado resultante.

Entre el paso 2 y el 3 imprime **el listado completo de tareas bloqueadas**, agrupado por proyecto y
ordenado por cantidad. Es la vista más útil del script: son las tareas que el dashboard va a marcar
`BLOCKED`, y la mayoría se desbloquean respondiendo una pregunta, no escribiendo código. Con
`-DryRun` es un informe de "qué me está frenando" que se puede correr cuando quieras sin tocar el VPS.

## Cómo se generaron los JSON

Un agente por proyecto leyó `docs/ROADMAP.md` y `docs/PROGRESS.md` completos, más el `git log`, y
produjo la lista de tareas **desde la primera etapa hasta que el proyecto esté terminado** — no solo
lo ya hecho. Después se ensambló el mapa `id → datos` aplicando estas reglas:

- **`progress` = `done / total`**, redondeado. Deja de ser un número puesto a mano: sale de la
  lista de tareas que se ve justo al lado, y se puede auditar.
- **`done` solo con evidencia de verificación real** documentada (probado end-to-end, verificado
  contra Postgres real, desplegado y comprobado). Nunca porque se escribió código.
- **`blocked` también por falta de definición**, no solo por impedimento externo — la regla de
  `SYNC_PROTOCOL.md`. Si el roadmap no dice con qué evidencia se verifica una tarea, va `blocked`.
- **`summary`** lleva para qué existe el proyecto, y **`events`** el resumen del sync, los bloqueos
  y el siguiente paso concreto. Así el detalle de cada proyecto en el dashboard responde solo las
  mismas preguntas que el mapa del portafolio.

## La capa editorial: `alias`, `priority`, `open_question`

Tres campos opcionales del contrato del sync que **no se derivan de nada** — los decides tú, viajan
en `progreso.json` y el mapa los lee del API. Se mantienen en el diccionario `PORTAFOLIO` del
generador, no en el código del frontend, así que cambiar una prioridad es editar un JSON y volver a
correr `sync-inbox.ps1`: sin recompilar ni redesplegar nada.

| Campo | Qué es |
|---|---|
| `alias` | Subtítulo corto del proyecto, máx 120 caracteres. Ej. `SCI 360 · Multimarcasa`. |
| `priority` | `NOW` · `NEXT` · `DECIDE` · `ON_TRACK` · `FROZEN`. Es lo que agrupa el mapa. |
| `open_question` | La única pregunta sin responder que desbloquea el proyecto. |

Los tres son opcionales y siguen la regla **ausente conserva / `null` limpia**: si el campo no viene
en el JSON, el backend deja el valor que ya tenía; si viene explícitamente como `null` o cadena
vacía, lo borra. Así un sync parcial nunca destruye la clasificación por accidente.

`priority` se valida contra el enum en tres capas — el generador, `sync-inbox.ps1` y el parser del
backend — porque un valor fuera del enum hace que el backend descarte la entrada entera en silencio.

## El mapa: ruta raíz de la app Angular

El mapa del portafolio **es** la vista principal del dashboard: `https://agent.srdejo.com.co/`.
El listado ordenable original no se perdió — se conserva entero en `/dashboard`, con su orden por
columnas, su feed de actividad y su modal de tareas bloqueadas. Hay navegación entre las dos en la
cabecera.

Archivos:

| Ruta | Componente |
|---|---|
| `/` | `features/portfolio/portfolio-map.ts` + `.html` — el mapa, agrupado por `priority`. |
| `/dashboard` | `features/projects/project-list.ts` — el listado de siempre. |
| `/agent` | `features/agent/agent-status.ts` — sin cambios. |
| `/:id` | `features/projects/project-detail.ts` — sin cambios. Va **último** en `app.routes.ts`, si no se traga `/dashboard`. |

El mapa hace dos llamadas — `GET /api/projects` y `GET /api/projects/blocked-tasks`, ambas ya
existían — y no pide el detalle de cada proyecto: por eso `summary`, `alias`, `priority` y
`openQuestion` se exponen también en el listado. Reusa `ProjectApiService`, los modelos tipados y los
tokens de Tailwind del proyecto (`surface-*`, `ink-*`, `status-*`), así que no introduce un segundo
sistema de diseño.

Se despliega con el flujo normal del frontend:

```powershell
cd D:\Workspace Daniel\01-activos\srdejo
.\infra\deploy.ps1        # menu -> agent-project -> opcion 2 (Deploy Frontend)
```

Ojo: los campos nuevos requieren que el **backend** con la migración V5 esté desplegado antes, o el
mapa recibirá `alias`/`priority`/`openQuestion` en `null` y todo caerá en "Sin clasificar".

## Ojo con esto al editar `sync-inbox.ps1`

Dos trampas de Windows PowerShell 5.1 que ya nos mordieron una vez:

- **El script se mantiene en ASCII puro, y se guarda con BOM UTF-8.** PowerShell 5.1 lee los `.ps1`
  sin BOM como ANSI, así que un guion largo o una tilde se convierte en basura y el parser falla con
  un error engañoso (`The string is missing the terminator`) en una línea que no tiene nada que ver.
  Si agregas texto con acentos, guarda el archivo como *UTF-8 con BOM*.
- **`"$id:"` dentro de una cadena no es lo que parece.** PowerShell lo interpreta como una referencia
  con drive (`$env:`, `$script:`) y falla con `Variable reference is not valid`. Hay que escribir
  `"${id}:"`.

## Ojo con esto al regenerar

- `tasks[].status` va en **minúsculas** (`done|wip|blocked|todo`); el `status` del proyecto va en
  **MAYÚSCULAS** (`IN_PROGRESS|BLOCKED|STARTED|COMPLETED`). Una tarea con `"BLOCKED"` se descarta
  entera y no cuenta para el contador del dashboard.
- El contador "BLOCKED" del listado suma **`tasks[].status`**, no el `status` del proyecto. Un
  proyecto marcado `BLOCKED` sin ninguna tarea `blocked` sale con 0.
- `last_modified` es la fecha del **estado que se describe** (aquí: la del último commit del repo),
  no la del momento del sync. Si no cambia, el backend no actualiza nada.
