# PROGRESS.md

Estado del proyecto a **2026-08-19**. Actualizar este archivo cada vez que se cierre una tarea del ROADMAP — no dejarlo desactualizado.

## Estado actual

Etapa 5 — Sync recurrente + deploy a `nolost-vps` (🟡 el código está listo y verificado localmente; falta el deploy real al servidor y programar el envío desde OpenClaw).

## Tarea actual

Ninguna en ejecución. Se completó el ciclo end-to-end local: backend (Postgres + JPA + inbox sync) ↔ frontend (dashboard real), más la migración de `nolost` al formato estándar de docs y la actualización del skill `senior-backend-dev` a Spring Data JPA.

## Completado

- **Backend**: reemplazado el scaffold JdbcTemplate por Spring Data JPA + Hibernate. Esquema Flyway (`projects`, `project_snapshots`, columnas `JSONB` vía `@JdbcTypeCode`). `modules:parser` valida/normaliza el JSON de sync (`SyncPayloadParser`). `modules:projects` expone `GET /api/projects`, `GET /api/projects/{id}` y el contrato `ProjectSyncPort` para aplicar syncs. `modules:progress` corre `InboxSyncJob` (`@Scheduled`) que escanea `data/inbox/`, valida, hace dedupe por hash SHA-256 y mueve los archivos a `processed/`/`rejected/`. **Verificado**: `./gradlew build` compila; con Postgres local (`docker-compose up -d`) y `bootRun`, un JSON de ejemplo depositado en el inbox se sincronizó y apareció correctamente en ambos endpoints (`curl`).
- **Frontend**: `ProjectApiService` (HttpClient) reemplaza a `ProjectMockService` (eliminado). Modelos TS (`ProjectSummary`, `ProjectDetail`, `ProjectListResponse`) alineados al shape real del backend. **Verificado**: `npm run build` compila; con el backend corriendo, `npm start` sirvió el dashboard y los endpoints `/api/projects`/`/api/projects/{id}` respondieron correctamente vía el proxy — verificado por `curl`, no visualmente en navegador.
- **Documentación**: `docs/SYNC_PROTOCOL.md` (contrato JSON completo), `docs/DEPLOYMENT.md` (systemd + nginx + estructura de directorios en el VPS), `deploy.ps1` (modelado en `nolost/deploy.ps1`). `docs/ARCHITECTURE.md`/`DECISIONS.md`/`ROADMAP.md` actualizados a reflejar Postgres+JPA y el sync por archivo (no por endpoint público).
- **Skill global `senior-backend-dev`** (`C:\Users\Daniel\.claude\skills\senior-backend-dev\`): actualizado para usar Spring Data JPA como convención por defecto en vez de JdbcTemplate, con nueva referencia `references/spring-data-jpa.md`. Los proyectos existentes (`consulting`/`distriapp`/`hotel`) no se tocan.
- **`nolost`** (repo hermano): creado `ROADMAP.md` (6 fases, checkboxes) y reescrito `PROGRESS.md` en el formato estándar, a partir del plan ya documentado en su `CLAUDE.md` §7 — sin inventar alcance nuevo.

## En progreso

Nada.

## Siguiente

- Desplegar `agent-project` en `nolost-vps` siguiendo `docs/DEPLOYMENT.md` (requiere acceso SSH al servidor, que esta sesión no tiene).
- Programar en OpenClaw (máquina del usuario) la generación y el envío periódico del JSON de sync de cada proyecto, según `docs/SYNC_PROTOCOL.md`.
- Verificar visualmente en navegador que el dashboard replica el mockup original.
- Agregar las pruebas unitarias pendientes (`SyncPayloadParser`, `ProjectSyncService`) marcadas sin verificar en `ROADMAP.md`.

## Bloqueadores

Ninguno técnico. El deploy real depende de acceso al servidor `nolost-vps`, fuera del alcance de esta sesión.

## Última actualización

2026-08-19
