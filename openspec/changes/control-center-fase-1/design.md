# Design

## Context

Estado actual (Fase 0, observado en código):

| Dato | Origen hoy | Qué pasa con él |
|---|---|---|
| `progress`, `status`, `verify`, `stage`, `tasks[]` | `infra/portfolio/portfolio.py` los **calcula** a partir de los checkboxes de `docs/ROADMAP.md` | Se reemplazan: el generador reporta hechos y el backend deriva (D2) |
| `name`, `repo`, `summary`, `stack`, `alias`, `priority`, `priority_rank`, `open_question` | `infra/portfolio/portfolio.json`, escrito a mano | `priority` → `state`, `open_question` → `blockers` (D7) |
| `last_modified`, `commit` | último commit, o `mtime` de ROADMAP si hay cambios sin commitear | Se sustituye por el hash de contenido (D3) |
| `checks`, `events` | `checks` vacío; `events` sintéticos generados por el script | Se eliminan |
| `project_snapshots` | una fila por cada update, con el % global | Se descarta (decisión del usuario); se reemplaza por historial por objetivo |
| Presencia del agente | WebSocket `/ws/agent` y `AgentPresenceService` (`platform:web-common`) | Se conserva |
| Autenticación | oauth2-proxy (Google, lista blanca) delante de todo el vhost | Se conserva; `/public/...` llega en la Fase 3 |
| OpenSpec | no se detecta; 14 repos lo tienen, todos con los mismos 6 comandos `opsx` | Entra en el snapshot (D4) |

Restricción que se mantiene: no hay endpoints de escritura. Los datos solo entran por el inbox de archivos.

## Goals / Non-Goals

**Goals:**
- Una sola regla de avance, implementada y testeada en el backend, que la Fase 3 (vista pública) pueda reutilizar sin copiarla.
- Un contrato de sync en el que el generador no decide nada: reporta lo que hay en los archivos.
- Un modelo relacional que permita consultar el trabajo de todos los proyectos (Mi trabajo) sin recorrer JSONB en Java.

**Non-Goals:**
- Agent Runs, adaptador de agente, evidencia de verificación, idempotency keys y auditoría (Fase 2).
- Vista pública (Fase 3).
- Dependencias entre tareas (no tienen fuente de datos).
- Markdown con formato enriquecido.
- Migrar el historial anterior.

## Decisions

### D0. El backend adopta el arquetipo `powerup-arquetipo-rx` (decisión del usuario)
Se reemplaza el backend por la estructura del arquetipo hexagonal reactivo (`D:\Workspace Daniel\02-sandbox\pragma\powerup-arquetipo-rx`): **un solo módulo Gradle, Java 26, Spring Boot 4.1, WebFlux, R2DBC, Flyway (conexión JDBC propia), MapStruct, Lombok, springdoc-openapi-webflux y JaCoCo**. Los nombres siguen sus convenciones: puertos `I…ServicePort` / `I…PersistencePort`, `…UseCase`, `…Handler`, `…RestController`, `…R2dbcAdapter`, `BeanConfiguration` y `ControllerAdvisor`.
- *Por qué*: el usuario pidió aprovechar la reestructuración para estandarizar sobre su arquetipo.
- *Alternativas descartadas*: adoptar solo la estructura hexagonal manteniendo MVC + JPA, y el arquetipo completo sobre Java 21. El usuario eligió el arquetipo completo.
- *Consecuencia*: se aparta de la convención "Spring Data JPA por defecto" de `CLAUDE.md`, que queda como excepción registrada en `DECISIONS.md`.

### D1. Capas y paquetes (`co.com.srdejo.agentproject`)
- `domain/model`: `ProjectSnapshot` (lo que reporta el generador), `Project`, `Task`, `OpenSpecChange`, `SyncRun`, etc. Java puro con Lombok y `record`s.
- `domain/usecase`: `ProgressCalculator` (la regla, pura), `SyncSnapshotUseCase` (aplicar el snapshot: hash, reemplazo, conservación ante errores, ausentes), `PortfolioUseCase`, `WorkUseCase`, `ContinuationUseCase` y `SyncHealthUseCase`.
- `domain/api`: puertos de entrada `I…ServicePort`, uno por use case.
- `domain/spi`: puertos de salida `IProjectPersistencePort` (lectura y reemplazo atómico del estado de un proyecto), `ISyncRunPersistencePort` y `IAgentPresencePort` (conectividad del agente).
- `application/handler`, `application/dto` y `application/mapper` (MapStruct): de modelo de dominio a DTO de respuesta.
- `infrastructure/input/rest`: los controladores WebFlux.
- `infrastructure/input/inbox`: `SnapshotParser` (Jackson; el formato JSON es un detalle de entrada, así que el dominio nunca ve `JsonNode`) e `InboxSyncJob` (`WatchService`, con I/O bloqueante aislada en `Schedulers.boundedElastic()`).
- `infrastructure/input/websocket`: el WebSocket del agente, portado a `WebSocketHandler` reactivo con el mismo protocolo y el mismo token.
- `infrastructure/out/r2dbc`: entidades, repositorios reactivos, adaptadores y conversores JSONB.
- `infrastructure/configuration`: `BeanConfiguration`, que conecta puertos y adaptadores (los use cases no llevan anotaciones de Spring).
- `infrastructure/exceptionhandler`, `infrastructure/documentation` y `infrastructure/logging` (un `WebFilter` con `requestId`).

El dominio solo depende de `reactor-core` y Lombok, igual que en el arquetipo. Con un único use case de sync ya no hace falta el puerto `RecentSyncRuns` de la versión multi-módulo: `WorkUseCase` y `ContinuationUseCase` leen los runs por `ISyncRunPersistencePort`.

### D2. El backend deriva estados y avance
El generador envía `checked`, `cancelled`, los marcadores de etapa, `has_criteria`, `backlog` y el alcance en texto. `ProgressCalculator` aplica las reglas de la spec `progress-model` cuando se aplica el sync, y persiste el estado derivado de cada tarea y los contadores del objetivo y del roadmap. Así las consultas no recalculan nada.
- *Por qué*: hoy la rúbrica vive en Python, sin tests, fuera del repo. Para la Fase 3 hace falta "la misma regla" en la vista pública.
- *Alternativa descartada*: calcular al consultar. Daría lo mismo con ~15 proyectos, pero el historial por objetivo necesita los contadores en el momento del sync de todas formas.

### D3. Detección de cambios por hash de contenido
El backend calcula el SHA-256 del JSON canónico de la entrada (claves ordenadas, sin `generated_at`) y lo compara con `project.content_hash`.
- *Por qué*: el dedupe por `last_modified` obligaba a usar `-Bump` cuando cambiaba la rúbrica, y a tirar de `mtime` para cambios sin commit. Con el hash, cualquier cambio en lo reportado se aplica, y nada más. Un cambio de regla en el backend se maneja con `progress_rule_version` (ver Risks).
- *Revierte* la decisión de 2026-08-19 ("de hash a `last_modified`"), que buscaba no tener dos mecanismos de detección. Ahora vuelve a haber uno solo, el hash. Se registra en `DECISIONS.md`.

### D4. Formato `snapshot.json` v2
Claves en snake_case, como el contrato actual.

```json
{
  "format_version": 2,
  "generator_version": "portfolio.py 2.0",
  "generated_at": "2026-10-15T07:00:00-05:00",
  "projects": {
    "distriapp": {
      "name": "DistriApp — SCI 360", "repo": "distriapp", "alias": "SCI 360 · Multimarcasa",
      "summary": "...", "state": "ACTIVE", "priority_rank": 1, "stack": ["Java 21"],
      "blockers": [{"id": "pagos", "type": "PRODUCT_DECISION", "description": "...", "affects": ["Etapa 4"]}],
      "source_commit": "8413025", "source_modified_at": "2026-10-14T18:00:00-05:00",
      "roadmap": {
        "objective": {"name": "MVP E2E sin mocks", "criteria": "...", "scope": ["Etapa 1", "openspec:mvp-e2e-sin-mocks"], "target_date": null},
        "achieved": [{"date": "2026-09-04", "name": "Gobernanza", "criteria": "..."}],
        "stages": [{"key": "Etapa 1", "title": "Etapa 1 — Backend arrancable", "marker": "in_progress",
                    "has_criteria": true, "backlog": false,
                    "tasks": [{"text": "...", "line": 42, "checked": true, "cancelled": false}]}]
      },
      "openspec": {
        "specs_count": 0, "archived_count": 3, "commands": ["apply", "archive", "explore", "propose", "sync", "update"],
        "changes": [{"name": "mvp-e2e-sin-mocks", "metadata": "schema: spec-driven\n",
                     "artifacts": {"proposal": {"content": "...", "truncated": false},
                                   "specs": [{"path": "specs/x/spec.md", "content": "...", "truncated": false}]},
                     "tasks": [{"number": "1.1", "text": "...", "section": "1. Setup", "line": 5, "checked": true, "cancelled": false}]}],
        "archived_in_scope": [{"name": "2026-09-01-algo", "tasks": []}]
      }
    }
  }
}
```

Las etapas de backlog se envían marcadas `backlog: true`. El backend las conserva para mostrarlas, pero no las cuenta. Así la regla de qué es backlog vive en el backend. El generador solo detecta los encabezados, con la misma lista de palabras que usa hoy.

### D5. Esquema (migración V7, destructiva)
`DROP` de `project_snapshots`, `sync_runs` y `projects`, y creación de:

- `project`: editorial (`name`, `repo`, `alias`, `summary`, `state`, `priority_rank`, `stack` JSONB), origen (`source_commit`, `source_modified_at`), sync (`content_hash`, `progress_rule_version`, `missing_since`, `read_error`, `read_error_at`), roadmap (`roadmap_detected`, `roadmap_done`, `roadmap_total`), **objetivo actual embebido** (`objective_name`, `objective_criteria`, `objective_target_date`, `objective_scope` JSONB con `{ref, kind, resolved}`, `objective_done`, `objective_total`), `achieved_objectives` JSONB, `blockers` JSONB (con `openTasksAffected` ya calculado) y OpenSpec (`openspec_detected`, `openspec_specs_count`, `openspec_archived_count`, `openspec_commands` JSONB).
- `roadmap_stage` (`id`, `project_id`, `ordinal`, `stage_key`, `title`, `marker`, `has_criteria`, `backlog`)
- `openspec_change` (`id`, `project_id`, `name`, `archived`, `ordinal`, `metadata`, `artifacts` JSONB, `last_parsed_at`, `parse_error`, `parse_error_at`; único por `project_id`+`name`)
- `task` (`id`, `project_id`, `source` ROADMAP/OPENSPEC, `stage_id` o `change_id`, `ordinal`, `number`, `text`, `section`, `line`, `checked`, `cancelled`, `status`, `blocked_reason`, `in_objective`, `held_by_blocker`)
- `objective_progress_point` (`project_id`, `objective_name`, `done`, `total`, `taken_at`)
- `sync_run` (`id`, `started_at`, `duration_ms`, `format_version`, `generator_version`, `generated_at`, `file_error`, `created_ids`/`updated_ids`/`unchanged_ids`/`missing_ids` JSONB, `rejected`/`read_errors`/`warnings` JSONB)

El objetivo, los cumplidos y los bloqueos van dentro de `project`, y no en tablas propias, porque siempre se leen y se reemplazan junto con el proyecto. Los artefactos van en JSONB por cambio: se leen siempre completos y no se consultan por dentro. Con R2DBC, las columnas JSONB se mapean con conversores de lectura y escritura (`io.r2dbc.postgresql.codec.Json` ⇄ tipos Java vía Jackson), registrados en `R2dbcCustomConversions`.

**Aplicar un proyecto `UPDATED`**: se borran y se reinsertan sus etapas, tareas, objetivo, bloqueos y cambios, todo en una transacción reactiva (`TransactionalOperator` en el adaptador R2DBC; el use case solo pide "reemplazar el estado del proyecto" por el puerto). Excepción: un cambio que llega con `error` conserva su fila y sus tareas previas, y solo actualiza `parse_error`.
- *Alternativa descartada*: un diff fila a fila. Las tareas no tienen un id estable en Markdown (el texto cambia) y el volumen es pequeño.

### D6. Ingesta en el inbox
`InboxSyncJob` conserva el `WatchService` con debounce y ahora espera `snapshot.json`. Si encuentra `progreso.json` o `nuevo.json`, registra el run con `file_error` "formato obsoleto" y los borra.

La detección de ausentes (`missing_since`) solo se aplica cuando el snapshot es válido a nivel de archivo, para que un archivo roto no marque todo como ausente.

### D7. Capa editorial nueva en `portfolio.json`
`priority` y `open_question` se eliminan. `state` y `blockers` se añaden. Mapeo inicial propuesto, que Daniel confirma en la tarea 1.3:

| antes | después |
|---|---|
| NOW | ACTIVE |
| NEXT | NEXT |
| DECIDE | ACTIVE, con su `open_question` como bloqueo `PRODUCT_DECISION` |
| ON_TRACK | MAINTENANCE |
| FROZEN | PAUSED |

### D8. Endpoints de solo lectura
- `GET /api/portfolio`
- `GET /api/projects/{id}`
- `GET /api/projects/{id}/changes`
- `GET /api/projects/{id}/changes/{name}`
- `GET /api/projects/{id}/changes/{name}/continuation`
- `GET /api/work`
- `GET /api/health/sync`

El `ControllerAdvisor` del arquetipo (`infrastructure/exceptionhandler`) responde 404 con cuerpo JSON (`NoDataFoundException`) y deja una línea de log por error. Esto cierra el pendiente anotado en `DECISIONS.md` ("Observabilidad HTTP"). Los endpoints se documentan con springdoc (`/swagger-ui.html`).

La continuación es un `GET` sin efectos y el texto se genera con una plantilla Java fija. No incluye la hora actual, solo la fecha de `generated_at`, así que el resultado es determinista mientras no llegue un sync nuevo. La única excepción es la advertencia de datos obsoletos, que depende del umbral de antigüedad.

### D9. Frontend
Se borran `features/projects`, `features/portfolio`, `shared/format` y `core/models/project.ts`. Se conservan `features/agent` y `core/services/agent-monitor.service.ts` (Etapa 7, en funcionamiento).

Estructura nueva:
- `core/models/` con `portfolio.ts`, `project.ts`, `change.ts`, `work.ts` y `health.ts`;
- `core/services/` con `portfolio-api.service.ts` y `sync-health.service.ts`;
- `layout/shell` con la cabecera, la salud y la navegación;
- `features/work`, `features/portfolio`, `features/project` (con sus pestañas) y `features/change`.

Rutas: `''`, `portfolio`, `projects/:id`, `projects/:id/changes/:name`, `agent` y `**` → `''`. Las rutas antiguas no se redirigen (decisión: sin compatibilidad).

Los artefactos se muestran en `<pre>` con interpolación. La gráfica del objetivo es un SVG inline sencillo, sin librería.
- *Alternativa descartada*: añadir `marked` o una librería de gráficos. Sumaría dependencias y obligaría a sanear el HTML generado.

### D10. Estrategia de tests
- `ProgressCalculatorTest` (unitario puro): cubre cada scenario de `progress-model` (estados, resolución de alcance, duplicados, cancelados, sin datos, sin objetivo, cumplido).
- `SnapshotParserTest`: cubre versión, entrada inválida aislada, bloqueo descartado, cambio con `error` y comandos.
- Tests unitarios de cada use case con puertos simulados (Mockito) y `StepVerifier`, como en el arquetipo: uno por scenario de `my-work`, `change-continuation` y `sync-health`.
- Tests de integración del adaptador R2DBC y del inbox (`@SpringBootTest`) contra **Postgres local real** (`agent_project_test`). Cubren `CREATED`/`UPDATED`/`UNCHANGED` por hash, la conservación ante `error` de repo y de cambio, `missing_since` y el formato obsoleto. El H2 del arquetipo no sirve aquí porque el esquema usa JSONB, y en la máquina de desarrollo no hay Docker para Testcontainers.
- `WebTestClient` para los controladores: forma de las respuestas y 404.
- `portfolio.py`: `unittest` sobre un árbol temporal (objetivo, cumplidos, etapas con marcador, tachado, OpenSpec, truncado, error).

Los tests actuales (`SyncPayloadParserTest`, `ProjectSyncServiceTest`) se borran junto con el código que cubren.

## Risks / Trade-offs

- [Los ROADMAP de los 11 repos aún no tienen `## Objetivo actual`] → El día del despliegue todos aparecerán como "Sin objetivo declarado". Por eso la tarea 1.3 redacta un borrador de objetivo por repo para que Daniel lo apruebe y se commitee **antes** del despliegue. El dashboard funciona igual sin objetivos.
- [Si cambia una regla del backend, no se recalcula nada, porque el hash del contenido no cambió] → Se guarda la versión de la regla (`progress_rule_version`) en `project`. Al arrancar, si difiere de la del código, se fuerza la reaplicación en el siguiente snapshot (el hash se ignora una vez).
- [Pérdida del historial] → Es una decisión explícita del usuario. Antes del despliegue se hace un `pg_dump` (tarea 8.1).
- [Snapshot grande: 14 repos con artefactos completos] → Límite de 200 KB por artefacto y sin contenido de los archivados. Se mide en la tarea 6.5.
- [La convención "Objetivo actual" puede colisionar con el criterio `## Objetivo` de una etapa] → Se reconoce solo el encabezado exacto de nivel 2 `## Objetivo actual`. El resto mantiene su significado actual. Hay test dedicado.
- [El VPS corre Java 21 y el arquetipo exige Java 26] → Antes de desplegar hay que instalar JDK 26 en `nolost-vps` y apuntar el unit `systemd` a ese `java` (tarea 8.2). Esto necesita `sudo`, así que lo hace Daniel. Mientras tanto, el backend actual sigue corriendo.
- [WebFlux cambia piezas que funcionaban (WebSocket del agente, filtro de logging)] → Se portan con el mismo protocolo, y se verifican con el daemon local real y con `bootRun` (tarea 2.3).
- [R2DBC no tiene transacciones implícitas ni mapeo JSONB automático] → `TransactionalOperator` explícito en el adaptador y conversores JSONB registrados. Los tests de integración contra Postgres real cubren ambos.
- [Despliegue coordinado: backend nuevo con generador viejo] → El generador viejo deja `progreso.json`; el backend lo registra como formato obsoleto y no toca los datos. A la inversa (generador nuevo con backend viejo), `snapshot.json` se ignora en silencio. Por eso se despliega primero el backend.

## Migration Plan

1. `pg_dump` de `agent_project` en el VPS.
2. Instalar JDK 26 en el VPS (Daniel, con `sudo`) y ajustar el `ExecStart` del unit.
3. Commitear las secciones `## Objetivo actual` aprobadas en cada repo.
4. Desplegar el backend: V7 elimina y crea las tablas. Verificar `GET /api/health/sync` (`stale=true` y sin runs).
5. Correr el generador nuevo y verificar el run y `GET /api/portfolio` con los 11 proyectos.
6. Desplegar el frontend.

**Rollback**: restaurar el `pg_dump`, volver a desplegar el jar y el frontend anteriores (los artefactos previos se conservan en `infra/`) y volver al `portfolio.py` anterior (`git revert` en `infra`).

## Open Questions

- El texto exacto de cada objetivo por repo: lo decide Daniel en la tarea 1.3. No cambia ni las specs ni el diseño.
