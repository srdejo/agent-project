# Proposal

## Why

Hoy el porcentaje de avance de cada proyecto no responde a ninguna pregunta útil. Es la suma de todos los checkboxes de `docs/ROADMAP.md` (lo histórico, lo actual y lo futuro, todos con el mismo peso), y lo calcula un script fuera del backend. Datos reales del último sync:

- `micasachurch` marca **100%** aunque sigue pendiente el contenido real.
- `perla-ai` marca **27%** porque suma 20 fases, cuando su trabajo actual va 6/9.
- El trabajo de `nolost`, que vive en cambios OpenSpec, no aparece.

Ningún proyecto declara qué intenta lograr, así que "100%" no significa "listo" y "agregar una etapa" baja el número. Además, el dashboard no ve OpenSpec, que es donde se ejecuta el trabajo. Por eso retomar un cambio después de cortar una sesión obliga a reconstruir a mano dónde quedó.

Este cambio es una **reestructuración completa** del Project Control Center: contrato de sync, modelo de datos, API y frontend. Cubre la Fase 0 y la Fase 1 del documento "Nueva estructura del Project Control Center". El usuario autorizó romper la compatibilidad y descartar el historial actual, porque mide otra cosa.

## What Changes

- **Objetivo por proyecto como base del avance**: cada repo declara en su `docs/ROADMAP.md` una sección `## Objetivo actual` con nombre, criterio de cierre y alcance. El alcance referencia etapas del ROADMAP y cambios OpenSpec (`openspec:<nombre>`). El **% principal** = tareas completadas dentro del alcance / tareas no canceladas del alcance. El % del ROADMAP completo pasa a ser secundario, y los objetivos cumplidos quedan como historial (`## Objetivos cumplidos`). Un proyecto sin la sección muestra "Sin objetivo declarado".
- **Un `[x]` cuenta igual en ROADMAP y en `tasks.md`**: ambos se marcan por convención solo después de verificar. Se muestran como "completadas según el repo". La evidencia independiente es un indicador aparte, "no disponible" hasta la Fase 2.
- **El backend calcula, el generador solo reporta hechos**: el generador envía checkboxes, etapas, marcadores y referencias en crudo. El backend deriva el estado de cada tarea y el avance con una sola regla testeada en Java.
- **BREAKING. Contrato de sync v2**: `progreso.json`/`nuevo.json` se reemplazan por un único `snapshot.json` versionado (`format_version: 2`). Contiene el estado completo de cada repo: proyecto, objetivo, etapas y tareas del ROADMAP, cambios OpenSpec con artefactos y tareas, bloqueos y comandos `opsx`. Cada proyecto se reemplaza completo, sin la regla "ausente conserva / `null` limpia". Solo se actualiza si cambió su contenido (hash calculado por el backend), lo que elimina la necesidad de `-Bump` y el truco del `mtime`. Si un repo falla al leerse, se conserva su último estado válido. Un proyecto que deja de reportarse se marca como ausente, no se borra.
- **BREAKING. Esquema nuevo**: se eliminan `projects`, `project_snapshots` y `sync_runs` con su historial. Se crean tablas normalizadas: proyecto, objetivo, referencias de alcance, etapa, tarea, cambio OpenSpec, bloqueo, historial de avance por objetivo y sync run.
- **BREAKING. Modelo de estado**: se adoptan los estados de proyecto del documento (`ACTIVE`, `NEXT`, `PAUSED`, `BLOCKED`, `MAINTENANCE`, `COMPLETED`, `ARCHIVED`), declarados en la capa editorial, en lugar de `priority` (`NOW`/`NEXT`/`DECIDE`/`ON_TRACK`/`FROZEN`) y del `status` derivado. Las tareas pasan a `OPEN`, `IN_PROGRESS`, `BLOCKED`, `DONE` y `CANCELLED`. `open_question` se reemplaza por bloqueos tipados (`PRODUCT_DECISION`, …). Se eliminan `verify`, `checks`, `events` (generados artificialmente) y `updated`.
- **BREAKING. API nueva, de solo lectura**: `GET /api/portfolio`, `GET /api/projects/{id}`, `GET /api/projects/{id}/changes[/{name}[/continuation]]`, `GET /api/work` y `GET /api/health/sync`. Se eliminan `GET /api/projects` (listado) y `/api/projects/blocked-tasks`.
- **BREAKING. Frontend reconstruido**: se eliminan el listado, el mapa del portafolio, el detalle y `shared/format`. Las vistas nuevas son:
  - "Mi trabajo" en `/`;
  - portafolio filtrable en `/portfolio`;
  - detalle de proyecto por pestañas en `/projects/:id`;
  - detalle de cambio OpenSpec con instrucción de continuación para copiar en `/projects/:id/changes/:name`.

  Se conservan el monitor del agente (`/agent`) y su WebSocket.
- **BREAKING. Backend sobre el arquetipo `powerup-arquetipo-rx`** (`D:\Workspace Daniel\02-sandbox\pragma\powerup-arquetipo-rx`, decisión del usuario): arquitectura hexagonal reactiva en **un solo módulo Gradle**, con las capas `domain` (model, api, spi, usecase), `application` (handler, dto, mapper) e `infrastructure` (input/rest, input/inbox, out/r2dbc, configuration). Stack: **Spring WebFlux + Spring Data R2DBC, Java 26 y Spring Boot 4.1**, más MapStruct, Lombok, springdoc (Swagger) y JaCoCo. Desaparecen `bootstrap/`, `modules/*` y `platform/*`. El WebSocket del agente y el logging HTTP se portan a WebFlux.
- **Generador** (`infra/portfolio/portfolio.py`, fuera del repo): se reescribe para emitir el formato v2. `portfolio.json` gana `state` y `blockers` y pierde `priority` y `open_question`.

**Se mantiene**: la entrada exclusiva por archivo en el inbox (sin endpoints de escritura), la protección del sitio con oauth2-proxy, el WebSocket del agente, el logging HTTP, el despliegue y la regla de que nada escribe en los repos ni en `openspec/`.

## Capabilities

### New Capabilities
- `sync-snapshot`: contrato v2 del snapshot completo por repo, generación (ROADMAP, objetivo, OpenSpec, comandos), validación, reemplazo por proyecto, conservación ante errores y proyectos ausentes.
- `progress-model`: formato de `## Objetivo actual`, resolución del alcance, estados de tarea, cálculo del avance del objetivo y del ROADMAP, objetivos cumplidos e historial por objetivo.
- `project-blockers`: bloqueos y decisiones tipados por proyecto; una decisión, un registro.
- `sync-health`: estado del sync y del agente por separado, antigüedad de los datos y modo obsoleto.
- `my-work`: vista "Mi trabajo" con el trabajo accionable del portafolio.
- `portfolio-view`: API y vistas del portafolio y del detalle de proyecto, con filtros y estados de proyecto.
- `change-continuation`: detalle de un cambio OpenSpec con artefactos e instrucción de continuación para copiar.

### Modified Capabilities
(ninguna: `openspec/specs/` está vacío en este repo)

## Impact

- **Backend**: se reemplaza entero por la estructura del arquetipo reactivo (un solo módulo, WebFlux + R2DBC). Se conservan las migraciones V1–V6 y la nueva V7 elimina las tablas actuales y crea las nuevas. La convención "Spring Data JPA por defecto" de `CLAUDE.md` deja de aplicar a este repo; queda registrada como excepción en `DECISIONS.md`.
- **Operación**: el VPS necesita **JDK 26** para ejecutar el jar (hoy corre Java 21), y hay que ajustar `systemd`/`deploy.ps1` en consecuencia.
- **Frontend**: se borran `features/projects`, `features/portfolio`, `shared/format` y `core/models/project.ts`, que se reemplazan.
- **Fuera del repo**: `infra/portfolio/portfolio.py`, `portfolio.json` y `README.md` se reescriben. También hay que añadir `## Objetivo actual` al `docs/ROADMAP.md` de cada repo; el contenido de cada objetivo lo decide Daniel.
- **Docs**: `README.md` (estándar: sección de objetivo), `CLAUDE.md` (módulos), `docs/SYNC_PROTOCOL.md` (reescrito para v2), `ARCHITECTURE.md`, `DECISIONS.md`, `ROADMAP.md` y `PROGRESS.md`.
- **Datos**: se pierde el historial de `project_snapshots` (decisión explícita). Antes de desplegar se toma un `pg_dump`, que sirve como rollback.
- **Despliegue**: backend, generador y frontend deben salir juntos. El generador viejo dejaría `progreso.json` en un inbox que ya no lo procesa: se registra el error en el sync run y no se pierde nada.
