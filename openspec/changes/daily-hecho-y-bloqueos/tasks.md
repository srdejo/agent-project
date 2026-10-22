# Tasks

## 0. Prerrequisito

- [x] 0.1 Archivar `daily-y-limpieza` (marcar su 3.2 con la evidencia de que `/api/daily` responde en producción) para que `openspec/specs/daily-plan` exista. Verificar: `openspec list --specs` muestra `daily-plan` y `openspec validate daily-hecho-y-bloqueos` pasa.

## 1. Generador: bloque `activity` (workspace raíz)

- [x] 1.1 `infra/portfolio/portfolio.py` agrega `activity: {since, projects: {id: [{sha, at, subject}]}}` (D2), sin tocar las entradas ni `format_version`. Verificar con un test nuevo en `test_portfolio.py`, sobre un repo git temporal: SHA sin duplicados, sin merges, máximo 30, más recientes primero, y que los commits no aparecen dentro de la entrada del proyecto.
- [x] 1.2 Documentar `activity` y el marcador de bloqueo en `infra/portfolio/README.md`. Verificar: `sync-portfolio.ps1 -OnlyBuild` genera un `snapshot.json` con `activity` para los 12 proyectos.

## 2. Backend: marcador de bloqueo

- [x] 2.1 `ProgressCalculator`: marcador de D5 en tareas abiertas de ROADMAP y OpenSpec, y `RULE_VERSION = 2`. Verificar con tests unitarios: los tres formatos, el corte del motivo, "queda bloqueado" sin dos puntos no marca, y una tarea `[x]` con el marcador sigue `DONE`.
- [x] 2.2 Verificar en `WorkUseCase` (test con puertos en memoria) que un cambio cuya única tarea abierta está bloqueada no aparece en "Continuar", y que una tarea bloqueada no aparece en "Accionables".

## 3. Backend: actividad

- [x] 3.1 Flyway `V8__repo_activity.sql` con `repo_commit` y `task_closed` (D3, D4), sus entidades, repositorios y el adaptador de `IActivityPersistencePort`. Verificar: el test de integración contra `agent_project_test` migra, inserta y lee.
- [x] 3.2 `SnapshotParser` lee `activity` (opcional) y descarta con aviso los commits inválidos, y `SnapshotBatch` lo lleva. Verificar con tests del parser: con `activity`, sin `activity` y con un commit sin `sha`.
- [x] 3.3 `SyncSnapshotUseCase`: reemplaza los commits por proyecto y registra `task_closed` con el diff de D4 antes de reemplazar un proyecto `UPDATED`. Verificar con tests: una tarea que pasa a `[x]` se registra con `generated_at`, un cambio nuevo ya marcado no se registra, un proyecto `CREATED` no registra nada, un sync con la entrada idéntica y solo `activity` distinta deja `UNCHANGED`, y el recálculo por `RULE_VERSION` no registra cierres.
- [x] 3.4 Documentar en `docs/SYNC_PROTOCOL.md` el bloque `activity`, el marcador de bloqueo y el registro de tareas cerradas. Verificar: el ejemplo de esquema incluye `activity` y la sección "Cómo aplica el backend" describe el diff.

## 4. Backend: daily

- [x] 4.1 `Daily` con `doneSince`, `done` y `blockers` (D7), sin `needs`. `DailyUseCase` calcula el período (D6), agrupa "Hecho" por proyecto (8 commits y `moreCommits`) y arma "Bloqueos" (editoriales de `ACTIVE`/`NEXT`/`BLOCKED` por prioridad, y después las tareas con marcador). El `text` queda en el orden Hecho / Para hoy trabajaré en / Bloqueos / comandos / continuación. Verificar con `DailyUseCaseTest`: el escenario del lunes, el tope de 8 con "2 más", el bloqueo de un proyecto que no es el foco, la 4.9 en bloqueos y fuera del foco, sin actividad, sin bloqueos, y el determinismo.
- [x] 4.2 DTO, mapper y OpenAPI de `GET /api/daily`. Verificar con `RestApiIntegrationTest` sobre datos sembrados: forma de `done` y `blockers`, ausencia de `needs`, y que `text` contiene las tres secciones en orden.
- [x] 4.3 `docs/ARCHITECTURE.md` actualizado con `/api/daily`, las tablas nuevas y el puerto de actividad. Verificar: `./gradlew build` en verde, con JaCoCo incluido.

## 5. Frontend

- [x] 5.1 `core/models/daily.ts` y el bloque "Hoy" (`features/work/daily.*`) con Hecho / Para hoy trabajaré en / Bloqueos, enlaces al proyecto o cambio, "Copiar daily" y el aviso `stale`. Verificar: `npm run build` y `npm test` en verde, y en Chrome headless contra el backend local con datos reales que se ven las tres secciones, que lo copiado coincide con `text` y que no hay overflow a 375 px ni a 1280 px.

## 6. Despliegue

- [ ] 6.1 Commit del generador en el repo raíz y de agent-project. Desplegar agent-project (backend + frontend) con la confirmación de Daniel y correr `sync-portfolio.ps1`. Verificar por loopback en el VPS: `GET /api/daily` trae `done` con commits de los últimos días, `blockers` incluye el proveedor de pagos de subscription-hub, y el foco no es `distriapp` 4.9. Daniel lo ve en https://agent.srdejo.com.co/.
- [x] 6.2 `docs/PROGRESS.md` con el estado del cambio y la evidencia del despliegue. Verificar: describe el daily con sus tres secciones y la fecha de la verificación en producción.
