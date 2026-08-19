# ROADMAP.md

Etapas hacia una primera versión funcional end-to-end de Agent Project (el backend de sync + dashboard, no el estándar en sí — ver `README.md` raíz para la visión del estándar, y `docs/OPENCLAW_PROJECT_CONTROL_CENTER.md` para el spec de producto detallado). Refleja el estado **real** del código (ver `ARCHITECTURE.md`), no un roadmap genérico.

Estado de una etapa: 🔴 no iniciada · 🟡 en curso · 🟢 cerrada.

Checkboxes `[x]` solo se marcan cuando la tarea fue **verificada en el código** (compila/corre/pasa), no cuando "se escribió".

---

## Etapa 0 — Gobernanza del proyecto 🟢

- [x] Crear `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`, `docs/ROADMAP.md`, `docs/PROGRESS.md`.
- [x] Crear scaffolding `backend/` (Gradle multi-módulo) y `frontend/` (Angular).
- [x] Verificar que `./gradlew build` compila.
- [x] Verificar que `npm install && npm run build` compila el frontend.

## Etapa 1 — Contrato de sync + validación 🟢

**Objetivo**: definir qué envía OpenClaw y cómo el backend lo valida (reemplaza la idea original de "parser de Markdown en el servidor" — ver `docs/DECISIONS.md`).

- [x] Documentar el esquema JSON en `docs/SYNC_PROTOCOL.md`.
- [x] `SyncPayloadParser` (`modules:parser`) — valida campos requeridos, enums, normaliza opcionales.
- [ ] Pruebas unitarias de `SyncPayloadParser` contra payloads de ejemplo (válidos e inválidos).

## Etapa 2 — Store de proyectos (Postgres + JPA) 🟢

- [x] Esquema Flyway (`projects`, `project_snapshots`).
- [x] `ProjectEntity`/`ProjectSnapshotEntity` + `ProjectJpaRepository`/`ProjectSnapshotJpaRepository`.
- [x] `ProjectSyncService` (upsert con dedupe por hash, crea el proyecto si no existía).
- [ ] Pruebas unitarias/`@DataJpaTest` de `ProjectSyncService` (creación, actualización, no-op si el hash no cambió).

## Etapa 3 — API de progreso 🟢

- [x] `GET /api/projects` — lista con stats agregados (count, avg, blocked, verified) y `series` por proyecto.
- [x] `GET /api/projects/{id}` — detalle completo + historial de snapshots.
- [x] Verificado con `curl` contra un proyecto sembrado manualmente.

## Etapa 4 — Dashboard 🟢

**Objetivo**: visualización descrita en `docs/OPENCLAW_PROJECT_CONTROL_CENTER.md` (mockup "OpenClaw Control Center" importado de Claude Design).

- [x] Vista de listado multi-proyecto (stats globales + lista + actividad del agente).
- [x] Vista de detalle de un proyecto (historial de progreso, completado/siguiente/bloqueado, evidencia de verificación, actividad).
- [x] Conectado a la API real (`ProjectApiService`, `ProjectMockService` eliminado).
- [ ] Verificar visualmente en navegador contra el mockup original (solo verificado por build + curl en esta sesión, no visualmente).

## Etapa 5 — Sync recurrente + deploy a `nolost-vps` 🟡

**Objetivo**: que el dashboard, corriendo en `nolost-vps`, se mantenga actualizado con el avance real de `consulting`, `distriapp`, `hotel`, `nolost` y `agent-project`.

- [x] `InboxSyncJob` (`modules:progress`, `@Scheduled`) — escanea el inbox, aplica los cambios, mueve archivos a `processed/`/`rejected/`.
- [x] Verificado localmente: archivo de ejemplo en el inbox → aparece en `GET /api/projects` tras el poll.
- [x] `deploy.ps1` (build + scp + restart de `agent-project`/nginx en `nolost-vps`).
- [x] `docs/DEPLOYMENT.md` (systemd, nginx, estructura de directorios esperada en el VPS).
- [ ] Desplegar realmente en `nolost-vps` (requiere acceso SSH que esta sesión no tiene).
- [ ] `nolost` migrado a `ROADMAP.md`/`PROGRESS.md` con checkboxes (hecho — ver el repo `nolost`), pero falta que OpenClaw efectivamente genere y envíe su primer JSON de sync.
- [ ] Configurar y programar OpenClaw (en la máquina del usuario) para generar y enviar el JSON de cada proyecto — fuera del alcance de este repo, ver nota en `docs/SYNC_PROTOCOL.md`.
