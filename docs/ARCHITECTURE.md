# ARCHITECTURE.md

Arquitectura tal como existe hoy en el código, no la aspiracional. El plan de lo que falta está en `ROADMAP.md`; el porqué de cada decisión, en `DECISIONS.md`. La reestructuración actual es el cambio OpenSpec `openspec/changes/control-center-fase-1/`.

## Vista general

```
agent-project/
  backend/    API Spring WebFlux (arquetipo hexagonal reactivo), R2DBC + Postgres
  frontend/   SPA Angular (standalone components)
  docs/       Gobernanza del repo
  openspec/   Cambios y specs OpenSpec de este repo
```

`agent-project` es la implementación de referencia del estándar descrito en `README.md`: recibe el estado de los repos del workspace y lo presenta como un centro de control ("Mi trabajo", portafolio, detalle de proyecto y de cambio OpenSpec).

## Cómo entran los datos

El backend **no** clona repos ni lee Markdown. El generador `infra/portfolio/portfolio.py` corre en la máquina del desarrollador y deja un `snapshot.json` (formato v2, `docs/SYNC_PROTOCOL.md`) en el inbox del servidor. `InboxSyncJob` lo detecta con `WatchService`, lo valida y aplica proyecto por proyecto, y lo borra.

```
infra/portfolio/portfolio.py --scp--> data/inbox/snapshot.json --WatchService--> InboxSyncJob
   (lee ROADMAP.md, openspec/,                                                       │ SnapshotParser
    portfolio.json de cada repo)                                                     ▼
                                                             SyncSnapshotUseCase ── ProgressCalculator
                                                                       │ IProjectPersistencePort
                                                                       ▼
                                                         Postgres (project, task, openspec_change, …)
                                                                       │
                                     GET /api/portfolio · /api/projects/** · /api/work · /api/daily · /api/health/sync
                                                                       ▼
                                                               Frontend (dashboard)
```

La presencia del agente usa el WebSocket nativo `/ws/agent`. El cliente local `infra/openclaw-daemon` es el emisor autenticado (`?role=agent&token=…`), y el dashboard es un receptor de solo lectura. El estado del agente vive en memoria: un reinicio lo devuelve a `offline`.

No existe ningún endpoint HTTP de escritura. Todo el vhost (`/`, `/api/`, `/ws/`) está detrás de **oauth2-proxy** (login de Google con lista blanca de correos, `infra/vps/oauth2-proxy/`) mediante `auth_request` de nginx. El backend no conoce al usuario.

### Origen de cada dato

| Dato | Origen |
|---|---|
| `name`, `repo`, `alias`, `summary`, `state`, `priority_rank`, `stack`, `blockers` | Capa editorial: `infra/portfolio/portfolio.json`, escrita a mano. |
| Etapas, tareas del ROADMAP, objetivo actual, objetivos cumplidos | `docs/ROADMAP.md` de cada repo, reportado en crudo por el generador. |
| Cambios OpenSpec, artefactos, tareas de `tasks.md`, comandos `opsx` | `openspec/` y `.claude/commands/opsx/` de cada repo. |
| Estado de cada tarea, avance del objetivo y del roadmap, tareas afectadas por cada bloqueo | **Derivados en el backend** por `ProgressCalculator` al aplicar el snapshot (spec `progress-model`). |
| `source_commit`, `source_modified_at` | Git del repo, o el `mtime` de los archivos sin commitear. |
| Commits recientes | Bloque `activity` del snapshot (7 días, fuera de las entradas), guardado en `repo_commit`. |
| Tareas cerradas | **Derivadas en el backend**: al aplicar un proyecto `UPDATED`, cada tarea que pasó de abierta a `[x]` se registra en `task_closed`. |
| Tareas bloqueadas por texto | **Derivadas en el backend** por `ProgressCalculator`: `Bloqueada: <motivo>` en el texto de la tarea. |
| Historial de avance | `objective_progress_point`, un punto por cada `CREATED`/`UPDATED` de un proyecto con objetivo. |
| Salud del sync | `sync_run`, una fila por cada procesamiento del inbox. |
| Conectividad del agente | Heartbeat del WebSocket `/ws/agent`, nunca "la API respondió". |
| Evidencia de verificación independiente | No existe todavía (Fase 2). El dashboard la muestra como "no disponible". |

## Backend

Sigue el arquetipo `powerup-arquetipo-rx` (`D:\Workspace Daniel\02-sandbox\pragma\powerup-arquetipo-rx`): un solo módulo Gradle, Java 26, Spring Boot 4.1, WebFlux, Spring Data R2DBC (`r2dbc-postgresql`), Flyway con conexión JDBC propia, MapStruct, Lombok, springdoc-openapi-webflux y JaCoCo. Jackson es la versión 3 (`tools.jackson`), la que trae Boot 4.

```
backend/src/main/java/co/com/srdejo/agentproject/
  domain/                         Java puro + reactor-core + Lombok; no conoce Spring ni R2DBC
    model/                        ProjectSnapshot (lo reportado), Project, Stage, Change, Task, SyncRun, …
    model/view/                   modelos de lectura: ProjectCard, ProjectDetail, ChangeList, Work, SyncHealth, …
    api/                          puertos de entrada: ISyncServicePort, IPortfolioServicePort, IWorkServicePort,
                                  IContinuationServicePort, ISyncHealthServicePort
    spi/                          puertos de salida: IProjectPersistencePort, ISyncRunPersistencePort,
                                  IActivityPersistencePort, IAgentPresencePort
    usecase/                      ProgressCalculator (la regla), SyncSnapshotUseCase, PortfolioUseCase, WorkUseCase,
                                  ContinuationUseCase, SyncHealthUseCase, DailyUseCase
  application/
    dto/response/                 contratos JSON de la API
    mapper/                       MapStruct: modelo de lectura → DTO
    handler/                      orquestan puerto de entrada + mapper
  infrastructure/
    input/rest/                   PortfolioRestController, WorkRestController (todos GET)
    input/inbox/                  SnapshotParser (Jackson → dominio, hash canónico) e InboxSyncJob
    input/websocket/              AgentPresenceService + AgentWebSocketHandler (/ws/agent)
    out/r2dbc/                    entidades, repositorios reactivos, R2dbcEntityMapper/JsonbCodec, adaptadores
    out/agent/                    AgentPresenceAdapter (IAgentPresencePort)
    configuration/                BeanConfiguration (puertos ↔ adaptadores, Clock), WebSocketConfiguration
    exceptionhandler/, exception/ ControllerAdvisor: NoDataFoundException → 404 JSON
    logging/                      RequestLoggingWebFilter: una línea por request con requestId; redacta ?token=
    documentation/                OpenAPI (/swagger-ui.html, /v3/api-docs)
```

Reglas de dependencia: `infrastructure` → `application` → `domain`, nunca al revés. Los use cases no llevan anotaciones de Spring; los instancia `BeanConfiguration`. El mapeo entidad ⇄ dominio es manual (`R2dbcEntityMapper`), no MapStruct, porque casi todo es (de)serialización JSONB de tipos genéricos.

### Esquema (Flyway V1–V8)

V1–V6 son el modelo anterior. **V7** (`V7__control_center_v2.sql`) elimina `projects`, `project_snapshots` y `sync_runs` con su historial, y crea:

| Tabla | Contenido |
|---|---|
| `project` | Editorial; origen; bookkeeping del sync (`content_hash`, `progress_rule_version`, `missing_since`, `read_error`); contadores del roadmap; **objetivo actual embebido** (`objective_*`, `objective_scope` JSONB); `achieved_objectives`, `blockers` y `openspec_commands` como JSONB. |
| `roadmap_stage` | Una fila por etapa (`stage_key`, `title`, `marker`, `has_criteria`, `backlog`). |
| `openspec_change` | Una fila por cambio activo, o archivado si está en el alcance. Lleva `artifacts` JSONB (contenido) y `artifact_index` JSONB (cuáles existen, para los listados sin cargar el contenido). |
| `task` | Una fila por checkbox, con su `stage_id` o `change_id`; `status`, `blocked_reason`, `in_objective` y `held_by_blocker` derivados. |
| `objective_progress_point` | Historial del avance por objetivo. |
| `sync_run` | Cada procesamiento del inbox: duración, versión del formato y del generador, `generated_at`, error de archivo e ids creados, actualizados, sin cambios, ausentes, rechazados, con error de lectura y avisos. |

**V8** (`V8__repo_activity.sql`) agrega la actividad para la sección "Hecho" del daily:

| Tabla | Contenido |
|---|---|
| `repo_commit` | Commits del bloque `activity`, PK `(project_id, sha)`, sin FK a `project`. Se reemplazan por proyecto en cada sync que los trae. |
| `task_closed` | Historial de tareas cerradas entre dos syncs: origen, `scope` (cambio o etapa), número, texto y `closed_at` (el `generated_at` del snapshot). El sync solo inserta. |

Aplicar un proyecto cambiado reemplaza sus filas en una transacción (`TransactionalOperator`). Un cambio OpenSpec ilegible conserva su versión anterior.

### Endpoints (todos GET, solo lectura)

| Ruta | Qué devuelve |
|---|---|
| `/api/portfolio[?includeArchived=true]` | Tarjetas por `priority_rank`: objetivo (`percent` null = "Sin datos"; objetivo null = "Sin objetivo declarado"), roadmap secundario, bloqueo principal y `hasActionable`. |
| `/api/projects/{id}` | Detalle: objetivo con alcance resuelto y tareas agrupadas, etapas, bloqueos, historial y `evidence: NOT_AVAILABLE`. |
| `/api/projects/{id}/changes` | Cambios sin contenido de artefactos, comandos `opsx` y conteos. |
| `/api/projects/{id}/changes/{name}` | Artefactos (Markdown crudo) y tareas. |
| `/api/projects/{id}/changes/{name}/continuation` | Instrucción determinista para retomar el cambio, más el comando `opsx` sugerido si está instalado. |
| `/api/work` | Mi trabajo: `continue`, `actionable`, `decisions`, `review`, `activity`. |
| `/api/daily` | El daily (`DailyUseCase`): `doneSince` y `done` (commits y tareas cerradas por proyecto desde las 00:00 del último día anterior con actividad, máximo 8 commits por proyecto); `focus` y hasta 2 `alternatives` de otros proyectos, sacados de Mi trabajo y sin tareas bloqueadas; `blockers` (los editoriales de todos los proyectos `ACTIVE`/`NEXT`/`BLOCKED` por prioridad, y después las tareas bloqueadas por texto); `commands`, `continuation`, `stale` y `text` (todo en texto plano). Determinista para los mismos datos y el mismo día (America/Bogota). |
| `/api/health/sync` | `lastAttemptedSync`, `lastSuccessfulSync`, antigüedad, `stale` (umbral `agent-project.sync.stale-after`, por defecto `PT26H`) y `agentConnectivity`. |

Un id inexistente responde 404 con `{"message": …}`.

## Frontend

Angular 22, standalone components, sin `NgModule`, signals, Tailwind CSS (paleta del mockup "OpenClaw Control Center").

```
frontend/src/app/
  core/models/        project.ts, change.ts, work.ts, health.ts (contratos de la API)
  core/services/      portfolio-api.service.ts (todos los GET), sync-health.service.ts (cada 60 s),
                      agent-monitor.service.ts (WebSocket /ws/agent, solo lectura)
  layout/shell.ts     cabecera: navegación, antigüedad de los datos, estado del agente y aviso de datos obsoletos
  shared/labels.ts    etiquetas de estado/tarea/bloqueo y formato de antigüedad
  features/work/      "Mi trabajo" (ruta /)
  features/portfolio/ portafolio con filtros y orden en la URL (/portfolio)
  features/project/   detalle por pestañas: resumen, objetivo y tareas, cambios, bloqueos, actividad (/projects/:id)
  features/change/    artefactos como texto literal y la instrucción de continuación para copiar (/projects/:id/changes/:name)
  features/agent/     monitor del agente (/agent)
```

Los artefactos OpenSpec se muestran siempre por interpolación dentro de `<pre>`, nunca con `innerHTML`: son Markdown no confiable. La vista de cambio no tiene botones para ejecutar ni para enviar: solo permite copiar.

## Cómo correrlo

- **Backend**: `cd backend && ./gradlew bootRun`. Necesita la base `agent_project` en el Postgres local (ver `infra/`). El toolchain de Gradle usa JDK 26 (`~/.jdks/openjdk-26.x` o lo descarga). El directorio de trabajo de `bootRun` es `backend/`, así que el inbox por defecto es `backend/data/inbox/`.
- **Tests**: `cd backend && ./gradlew test`. Los tests de integración usan la base local `agent_project_test`, que se crea una vez con `CREATE DATABASE agent_project_test OWNER agent_project;`.
- **Frontend**: `cd frontend && npm install && npm start` (proxy a `localhost:8080`).

## Despliegue

Ver `docs/DEPLOYMENT.md`: `systemd`, nginx con oauth2-proxy, el puerto 8083 y `infra/deploy.ps1`. El jar nuevo **necesita JDK 26 en el VPS**; mientras no esté instalado, sigue corriendo la versión anterior.
