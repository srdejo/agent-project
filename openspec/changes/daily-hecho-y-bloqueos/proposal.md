# Proposal

## Why

El daily de producción (`GET /api/daily`, bloque "Hoy") solo responde "en qué trabajar". No dice qué se hizo desde la
última daily, porque el snapshot no trae commits ni el backend guarda qué tareas se cerraron. Además elige como foco
tareas que el repo ya declara bloqueadas: el 2026-10-22 el foco fue `distriapp` 4.9, cuyo texto empieza con
"Bloqueada (2026-10-22): …". Y "Para trabajar necesito" solo muestra los bloqueos de los tres proyectos elegidos, así que
se ocultan los de consulting, subscription-hub, micasachurch, perla-ai y srdejo-web. Daniel quiere que el daily responda
tres preguntas: qué se hizo, qué se puede hacer y qué está bloqueando.

## What Changes

- **Hecho**: el daily agrega una sección con los commits y las tareas cerradas de cada proyecto desde el último día con
  actividad anterior a hoy (así el lunes muestra el viernes).
  - `portfolio.py` (raíz del workspace) agrega al snapshot un bloque `activity` de primer nivel, con los commits de los
    últimos 7 días de cada repo. Es un campo opcional: `format_version` sigue en `2`.
  - El backend guarda esos commits y registra cada tarea que pasa de abierta a `[x]` entre dos syncs, con la fecha del
    snapshot que la cerró.
- **Tareas bloqueadas por texto**: una tarea de ROADMAP o de `tasks.md` cuyo texto declare `Bloqueada: <motivo>` (o
  `Bloqueada (<fecha>): <motivo>`, o `(bloqueada: <motivo>)`) queda en estado `BLOCKED` con ese motivo. Así deja de
  salir en "Continuar", "Accionables ahora" y como foco del daily.
- **Puedo hacer**: el foco y las alternativas se eligen igual que hoy, pero sobre tareas no bloqueadas (sale solo del
  punto anterior).
- **Bloqueos**: reemplaza a "Para trabajar necesito". Lista los bloqueos editoriales de todos los proyectos `ACTIVE`,
  `NEXT` y `BLOCKED` por prioridad, y después las tareas bloqueadas por texto de esos proyectos.
  - **BREAKING (API interna)**: `needs` se reemplaza por `blockers` y se agregan `done` y `doneSince` en
    `GET /api/daily`. El único consumidor es el frontend, que se despliega junto.
- El bloque "Hoy" y el `text` copiable quedan con tres secciones: Hecho / Para hoy trabajaré en / Bloqueos. Los
  comandos y la continuación no cambian.

## Capabilities

### New Capabilities
- `repo-activity`: actividad reciente de cada repo (commits del snapshot y tareas cerradas entre syncs) disponible para
  el backend.
- `task-blocking`: convención de texto para declarar una tarea bloqueada y su efecto en el estado de la tarea.

### Modified Capabilities
- `daily-plan`: el endpoint y el bloque "Hoy" agregan "Hecho" y cambian "Para trabajar necesito" por "Bloqueos". Esta
  capability la crea el cambio `daily-y-limpieza`, todavía sin archivar: archivarlo antes que este (su tarea 3.2 ya se
  cumple, `/api/daily` responde en producción).

## Impact

- **Workspace raíz**: `infra/portfolio/portfolio.py` (bloque `activity`), sus tests y `infra/portfolio/README.md`.
- **Backend**: `SnapshotParser`, `SyncSnapshotUseCase` (diff de tareas cerradas), `ProgressCalculator` (marcador de
  bloqueo, `RULE_VERSION` 2), `DailyUseCase`, `Daily`/DTO/mapper, migración Flyway `V8` con dos tablas nuevas.
- **Frontend**: `features/work/daily.*` y `core/models/daily.ts`.
- **Docs**: `docs/SYNC_PROTOCOL.md` (bloque `activity` y marcador de bloqueo), `docs/ARCHITECTURE.md`,
  `docs/PROGRESS.md`.
- **Despliegue**: agent-project (backend + frontend) y una corrida de `sync-portfolio.ps1`.
