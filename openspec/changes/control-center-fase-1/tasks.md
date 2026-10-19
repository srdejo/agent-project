# Tasks

> Reestructuración completa del Project Control Center (Fase 0 + Fase 1). **Rompe la compatibilidad** del sync, la API, el esquema y el frontend, y descarta el historial de `project_snapshots` por decisión del usuario. El backend se rehace sobre el arquetipo `powerup-arquetipo-rx` (design D0).
>
> - Los grupos 1.3 y 6 tocan repos **fuera de este**: `infra/portfolio/` y el `docs/ROADMAP.md` de cada proyecto.
> - Ningún paso añade endpoints de escritura ni escribe en `openspec/` de otros repos.

## 1. Fase 0: estándar, decisiones y objetivos

- [x] 1.1 Agregar al `README.md` raíz (estándar Agent Project) las secciones `## Objetivo actual` y `## Objetivos cumplidos`, con su formato exacto (spec `progress-model`), la regla de referencias (`Etapa N`, `Fase N`, `openspec:<cambio>`), el tachado `~~…~~` como cancelación y un ejemplo completo. Verificar: el ejemplo del README, copiado tal cual a un ROADMAP de prueba, lo reconoce el generador de la tarea 6.1.
- [x] 1.2 Registrar en `docs/DECISIONS.md` las decisiones de este cambio:
  - reestructuración sin compatibilidad y descarte del historial;
  - objetivo declarado en el ROADMAP como base del avance;
  - `[x]` de ROADMAP y de `tasks.md` equivalentes, con la evidencia aparte;
  - el backend deriva los estados y el generador solo reporta;
  - vuelta al hash de contenido (revierte la decisión del 2026-08-19);
  - el backend pasa al arquetipo reactivo, como excepción a "JPA por defecto";
  - `priority`/`open_question` → `state`/`blockers`;
  - el sitio está protegido por oauth2-proxy (corrige "Sin módulo platform:security").

  Verificar: cada decisión tiene su motivo y su alternativa descartada, y `grep -n "oauth2-proxy" docs/DECISIONS.md docs/ARCHITECTURE.md` devuelve ambas.
- [ ] 1.3 Redactar, para cada uno de los 11 repos del portafolio, un borrador de `## Objetivo actual` (nombre, criterio de cierre y alcance con referencias a etapas y cambios reales) y el mapeo de `priority` → `state` y `open_question` → `blockers` (design D7). Presentarlo a Daniel en `RESUMEN-DIARIO.md` para su aprobación. **No commitear en los repos sin su aprobación.** Verificar: Daniel aprueba o corrige cada uno, y los aprobados quedan commiteados en la rama de trabajo de cada repo.

## 2. Contrato v2 y base del arquetipo

- [x] 2.1 Reescribir `docs/SYNC_PROTOCOL.md` para el formato v2 (design D4):
  - el archivo `snapshot.json` y la subida atómica;
  - el esquema completo con una tabla de campos;
  - la validación por entrada;
  - `error` por repo y por cambio;
  - el hash, los proyectos ausentes y el formato obsoleto;
  - el límite de 200 KB.

  Eliminar todo lo de `progreso.json`/`nuevo.json`, `last_modified` y "ausente conserva". Verificar: el ejemplo JSON del documento pasa por `SnapshotParser` en un test.
- [x] 2.2 Reemplazar `backend/` por la estructura del arquetipo (design D0/D1):
  - un solo módulo Gradle (Java 26, Spring Boot 4.1, WebFlux, R2DBC + `r2dbc-postgresql`, Flyway con JDBC propio, MapStruct, Lombok, springdoc-webflux, JaCoCo, `lombok.config`);
  - paquete `co.com.srdejo.agentproject.{domain,application,infrastructure}` y `AgentProjectApplication`;
  - `application.yml` con R2DBC, Flyway, el pool limitado del VPS, inbox, `stale-after` y el token del agente;
  - conservar las migraciones V1–V7.

  Borrar `bootstrap/`, `modules/` y `platform/`. Verificar: `./gradlew build` en verde y `bootRun` contra la base local con datos V1–V6 aplica V7 limpio (`\dt` muestra solo las tablas nuevas y `flyway_schema_history`).
- [x] 2.3 Portar a WebFlux las piezas que ya funcionaban:
  - el WebSocket `/ws/agent` (`WebSocketHandler`, mismo protocolo de `docs/AGENT_MONITOR.md`, token `AGENT_WS_TOKEN` solo para `role=agent`, heartbeat y offline a los 45 s, últimos 200 logs);
  - el logging HTTP (`WebFilter`, una línea por request con status y duración, y `requestId`).

  Verificar: `bootRun` + `infra/openclaw-daemon` (o un cliente WebSocket de prueba) recibe `agent_snapshot` y pasa a `online`, y cada request deja una línea en el log.
- [x] 2.4 `ControllerAdvisor` con `NoDataFoundException` → 404 con cuerpo JSON y una línea de log, y `OpenApiConfiguration` (springdoc). Verificar con `WebTestClient`: un recurso inexistente devuelve 404 JSON, y `/swagger-ui.html` responde en `bootRun`.

## 3. Dominio (`domain`)

- [x] 3.1 `domain/usecase/ProgressCalculator` (puro). Deriva los estados de tarea, resuelve el alcance (etapas por clave sin distinguir mayúsculas, cambios activos o archivados), deduplica las tareas, excluye backlog y cancelados del denominador, y calcula los contadores del objetivo y del roadmap, el objetivo cumplido y las tareas abiertas afectadas por cada bloqueo. Incluye `RULE_VERSION`. Verificar con `ProgressCalculatorTest`: un test por scenario de `progress-model` y de `project-blockers` ("Elementos afectados resueltos"). El caso `## Objetivo actual` frente a `**Objetivo**:` lo cubre el parseo en 6.1.
- [x] 3.2 Modelo de dominio (`ProjectSnapshot`, el estado derivado del proyecto, `SyncRun`), puertos `domain/spi` (`IProjectPersistencePort`, `ISyncRunPersistencePort`, `IAgentPresencePort`) y `SyncSnapshotUseCase` (`domain/api/ISyncServicePort`):
  - hash y versión de regla → `CREATED`/`UPDATED`/`UNCHANGED`;
  - reemplazo completo vía puerto;
  - conservación del cambio con `error` y del proyecto con `error` de repo;
  - punto de historial del objetivo;
  - ausentes;
  - registro del run.

  Verificar con tests unitarios (Mockito + `StepVerifier`) un caso por scenario de "Reemplazo completo", "Conservar el último estado válido" y "Proyectos ausentes" de `sync-snapshot`.
- [x] 3.3 Use cases de lectura con sus puertos de entrada:
  - `SyncHealthUseCase` (spec `sync-health`, umbral por defecto `PT26H`);
  - `PortfolioUseCase` (portafolio, detalle y cambios; spec `portfolio-view` y la parte de listado/detalle de `change-continuation`);
  - `ContinuationUseCase` (design D8);
  - `WorkUseCase` (spec `my-work`).

  Verificar con tests unitarios un caso por scenario de esas specs: entre otros, sin runs, último fallido y anterior correcto hace 30 h, 25 h no obsoleto, agente offline, proyecto sin objetivo, denominador 0, `ARCHIVED` excluido, continuación determinista, sin `apply`, todo hecho → `/opsx:archive`, `stale` → advertencia, pausado excluido, `affects` excluye accionables, orden de decisiones e ítems de revisión.

## 4. Adaptador de salida R2DBC (`infrastructure/out/r2dbc`)

- [x] 4.1 Entidades y repositorios reactivos de las tablas de V7, conversores JSONB (`R2dbcCustomConversions`), y adaptadores de `IProjectPersistencePort` (reemplazo atómico con `TransactionalOperator`, lecturas sin cargar artefactos en los listados) y de `ISyncRunPersistencePort`. Presencia del agente como adaptador de `IAgentPresencePort`. Conexión en `BeanConfiguration`. Verificar con tests de integración contra `agent_project_test`: crear, sin cambios, reemplazo, cambio ilegible conservado, cambio nuevo ilegible, cambio que desaparece, error de repo, ausente y vuelta, versión de regla y archivado en el alcance.

## 5. Entradas (`infrastructure/input`, `application`)

- [x] 5.1 `infrastructure/input/inbox`: `SnapshotParser` (Jackson → `ProjectSnapshot` de dominio, hash canónico, avisos) e `InboxSyncJob` (`WatchService` con debounce, I/O en `boundedElastic`, formato obsoleto, borrado del archivo). Verificar con `SnapshotParserTest` (versión 3, JSON inválido, entrada inválida aislada, bloqueo `LEGAL`, cambio con `error`, error de repo, ejemplo del protocolo y orden de claves en el hash) y con un test de integración del inbox (snapshot válido, ilegible, obsoleto y ausente). Verificar también en `bootRun` soltando un archivo en el inbox.
- [x] 5.2 Handlers, DTO y mappers (MapStruct) en `application`, y controladores WebFlux en `infrastructure/input/rest` con anotaciones OpenAPI para todos los endpoints de design D8. Verificar con `WebTestClient`: la forma de cada respuesta sobre datos sembrados, y 404 para un proyecto y un cambio inexistentes.
- [x] 5.3 Reescribir `docs/ARCHITECTURE.md` (arquetipo y capas, esquema, endpoints, flujo de datos v2, tabla "Origen de cada dato", cómo correr los tests de integración) y la sección Stack/Convenciones de `CLAUDE.md`. Verificar: `./gradlew build` en verde y cada endpoint y cada tabla aparece en el documento.

## 6. Generador (fuera de este repo: `infra/portfolio/`)

- [x] 6.1 Reescribir `portfolio.py` para emitir `snapshot.json` v2 (design D4). Parsea el ROADMAP en crudo: etapas con clave, marcador, criterio y backlog; tareas con `checked`/`cancelled`/línea; `## Objetivo actual` y `## Objetivos cumplidos`. No calcula ningún porcentaje ni estado. Verificar con `unittest` sobre árboles temporales: objetivo con alcance, cumplidos, etapa backlog, tachado, `**Objetivo**:` de etapa que no se confunde con el objetivo, y un repo sin ROADMAP.
- [x] 6.2 Escaneo de `openspec/` en `portfolio.py`: cambios activos con sus artefactos presentes (límite de 200 KB y `truncated`), `metadata`, tareas (número, texto, sección, línea, `checked`, `cancelled`, anidadas), `archived_count`, `specs_count`, `commands` desde `.claude/commands/opsx/`, `archived_in_scope` solo con las tareas, y `{name, error}` ante fallos de lectura. Verificar con `unittest`: artefacto ausente, truncado, error de lectura, tarea anidada, tarea sin número y cambio archivado referenciado.
- [x] 6.3 Entrada con `error` de repo (sin abortar la corrida) y subida atómica de `snapshot.json` (`.tmp` + `mv`). Eliminar la generación de `progreso.json`/`nuevo.json`, `-Bump`, el cálculo de `last_modified` y los `events`. Verificar: un repo con el ROADMAP ilegible produce `{error}` y el resto se genera; `-DryRun` no sube nada.
- [ ] 6.4 Migrar `portfolio.json` al mapeo aprobado en 1.3 (`state`, `blockers`, sin `priority`/`open_question`) con validación previa a la subida, y reescribir `infra/portfolio/README.md` (la rúbrica queda como "hechos que se reportan"; la regla de avance apunta al spec del backend). Verificar: `.\sync-portfolio.ps1 -DryRun` no muestra errores de validación.
- [x] 6.5 `.\sync-portfolio.ps1 -OnlyBuild` y medición del tamaño de `out/snapshot.json`. Verificar: ningún artefacto supera 200 KB y el tamaño total queda anotado en `docs/PROGRESS.md`.

## 7. Frontend reconstruido

- [x] 7.1 Borrar `features/projects`, `features/portfolio`, `shared/format`, `core/models/project.ts` y `core/services/project-api.service.ts`. Crear los modelos y servicios nuevos (design D9) y `layout/shell` con la navegación y la cabecera de salud (antigüedad, aviso `stale`, agente). Rutas: `''`, `portfolio`, `projects/:id`, `projects/:id/changes/:name`, `agent`, `**`. Verificar: `npm run build` sin errores y `grep` sin referencias a los archivos borrados; con `stale-after` local `PT1M` aparece el aviso.
- [x] 7.2 `features/work`: las cinco secciones con estados vacíos, los enlaces y el recorte a 2 líneas con expansión. Verificar en el navegador con datos locales: cada sección con y sin datos, los enlaces al destino correcto y sin overflow horizontal a 375 px.
- [x] 7.3 `features/portfolio`: tarjetas (avance `NN% (done/total)`, "Sin objetivo declarado" o "Sin datos", roadmap secundario, acción principal), filtros y orden en `queryParams`. Verificar en el navegador: filtro `ACTIVE` + "con bloqueos" conservado al recargar, orden por avance y la acción "Declarar objetivo" en un proyecto sin objetivo; a 375 px sin overflow.
- [x] 7.4 `features/project`: pestañas Resumen (con la gráfica SVG del objetivo), Objetivo y tareas (alcance con avisos de referencias no resueltas, tareas agrupadas, "completadas según el repo" y "evidencia independiente: no disponible", roadmap fuera del objetivo plegado), Cambios OpenSpec, Bloqueos y decisiones, y Actividad. Verificar en el navegador con un proyecto real que tenga objetivo mixto (ROADMAP + OpenSpec) y con uno sin objetivo.
- [x] 7.5 `features/change`: artefactos presentes en `<pre>` (sin `innerHTML`), avisos de truncado y de error, tareas con estado, y un panel de continuación con `textarea` editable, el botón "Copiar" y el indicador de agente offline, sin botón de ejecutar. Verificar en el navegador: un artefacto de prueba con `<script>` se muestra como texto y el texto copiado conserva la edición.

## 8. Despliegue y cierre

- [ ] 8.1 Prueba end-to-end local: `bootRun` + `npm start` con el `out/snapshot.json` real de 6.5 en el inbox. Verificar:
  - Mi trabajo muestra cambios reales de `nolost`/`visionarios`;
  - los proyectos con objetivo aprobado muestran su % con numerador y denominador;
  - la continuación se copia;
  - `/api/health/sync` responde `stale=false`.
- [ ] 8.2 En el VPS:
  - JDK 26 instalado (lo hace Daniel con `sudo`) y el `ExecStart` del unit `agent-project.service` apuntando a él (`docs/DEPLOYMENT.md` actualizado);
  - `pg_dump agent_project` guardado con fecha;
  - despliegue del backend (`infra/deploy.ps1`) y `GET /api/health/sync` sin runs;
  - corrida del generador nuevo y `GET /api/portfolio` con los 11 proyectos;
  - despliegue del frontend.

  Verificar en `https://agent.srdejo.com.co` (con el login de oauth2-proxy): Mi trabajo, portafolio, un detalle de proyecto y un detalle de cambio cargan con datos reales, y el monitor del agente sigue recibiendo eventos.
- [ ] 8.3 Actualizar `docs/ROADMAP.md` (nueva etapa "Control Center v2 — Fase 0–1", con su propio `## Objetivo actual` para que agent-project use el estándar desde el primer día) y `docs/PROGRESS.md` (estado y siguiente paso: Fase 2). Marcar las casillas solo tras 8.1–8.2. Verificar: el próximo sync muestra agent-project con su objetivo y su %.
