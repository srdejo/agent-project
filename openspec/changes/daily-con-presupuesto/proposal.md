# Proposal

## Why

El daily propone un foco y dos alternativas sin saber cuánto Claude le queda a Daniel. Un día con el cupo semanal casi agotado debería proponer menos, y uno con margen, más. El cupo del plan no existe como dato local (solo `/usage` lo muestra), pero Claude Code sí guarda en la PC el uso de cada respuesta, con el directorio de la sesión. Decisión de Daniel: combinar ese historial con el % disponible que él lee en `/usage` (`docs/DECISIONS.md`, 2026-10-19).

## What Changes

- **Generador**: `infra/portfolio/portfolio.py` agrega al `snapshot.json` una sección opcional `usage`. Contiene el uso diario de los últimos 28 días, total y por repo, leído de `~/.claude/projects/*.jsonl`: cada respuesta se cuenta una sola vez y el repo se toma del `cwd` de la sesión. No sube contenido de las conversaciones, solo conteos.
- **Backend**: guarda esa serie y calcula el **costo por tarea**: tokens del período divididos por las tareas completadas en el mismo período, según el historial de avance. `GET /api/daily?availablePercent=N` usa el costo y el % para estimar cuántas tareas caben hoy y recorta las alternativas.
- **Frontend**: en el bloque "Hoy", un campo "% disponible según /usage" que se recuerda en el navegador, el presupuesto estimado y el uso de los últimos 7 días.
- Todo son **estimaciones**, y la vista lo dice.

No cambia: sigue sin haber ejecución en la PC ni endpoints de escritura. El % viaja como parámetro de un `GET`.

## Capabilities

### New Capabilities
- `usage-budget`: cómo se mide el uso de Claude, cómo se estima el presupuesto del día y cómo dimensiona el daily.

### Modified Capabilities
(ninguna: `daily-plan` sigue sin archivar en `daily-y-limpieza`; aquí solo se le agrega el recorte por presupuesto)

## Impact

- `infra/portfolio/portfolio.py` y sus tests: sección `usage`.
- `docs/SYNC_PROTOCOL.md`: sección opcional `usage`, compatible con `format_version` 2.
- **Backend**:
  - migración `V8` con la tabla `claude_usage_day`;
  - parser, puerto y adaptador de persistencia;
  - `UsageBudget` dentro de `DailyUseCase`;
  - parámetro `availablePercent`.
- **Frontend**: bloque "Hoy".
