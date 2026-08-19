# PROGRESS.md

Estado del proyecto a **2026-08-19**. Actualizar este archivo cada vez que se cierre una tarea del ROADMAP — no dejarlo desactualizado.

## Estado actual

Etapa 5 — Sync recurrente + deploy a `nolost-vps` (🟡 el protocolo de sync está rediseñado, implementado y **verificado end-to-end en local** contra Postgres/nginx reales; falta el deploy al servidor y que OpenClaw genere/envíe los archivos de verdad).

## Tarea actual

Ninguna en ejecución. Se cerró el rediseño del protocolo de sync (`progreso.json`/`nuevo.json`) y su verificación local completa.

## Completado

- **Backend — store y API** (Etapas 2–3): Spring Data JPA + Hibernate, esquema Flyway (`projects`, `project_snapshots`). `GET /api/projects`, `GET /api/projects/{id}`. **Verificado** con `curl`.
- **Frontend — dashboard** (Etapa 4): `ProjectApiService` contra la API real, vistas de listado y detalle. **Verificado**: build + `curl` contra el proxy; falta verificación visual en navegador real (pendiente, ver "Siguiente").
- **Backend — protocolo de sync rediseñado** (Etapa 1 + 5, 2026-08-19): reemplazado el modelo de un JSON por proyecto por dos archivos fijos agregados por id, `progreso.json` (proyectos existentes) y `nuevo.json` (proyectos nuevos). Motivo y detalle en `docs/DECISIONS.md`; contrato completo en `docs/SYNC_PROTOCOL.md`.
  - `modules:parser` — `SyncPayloadParser.parseBatch()` valida cada entrada del mapa de forma independiente (una entrada mala no bloquea al resto); `SyncPayload` gana `lastModified`.
  - `modules:projects` — `ProjectSyncPort` gana `exists(id)`; `ProjectSyncRequest`/`ProjectEntity`/`ProjectSyncService` usan `source_last_modified` (migración `V2__project_last_modified.sql`) en vez del hash SHA-256 anterior para decidir si hay que actualizar.
  - `modules:progress` — `InboxSyncJob` reescrito: busca `progreso.json`/`nuevo.json`, aplica las reglas de validación por archivo (progreso = solo ids existentes, nuevo = solo ids nuevos), y **borra ambos archivos siempre al terminar el poll**, haya habido cambios o no.
  - Intervalo de poll: `SYNC_POLL_INTERVAL_MS` default cambiado de 5 minutos a **6 horas** (pensado para la cadencia de OpenClaw, no para reaccionar al instante).
  - **Verificado end-to-end en local** (Postgres 17 + nginx del entorno `infra/` de `srdejo`, backend real con `bootRun`, vía `curl`): creación de proyecto nuevo vía `nuevo.json`; rechazo de un id desconocido en `progreso.json` (no crea, solo loguea warning); actualización de un proyecto existente con `last_modified` distinto (genera nuevo snapshot); no-op cuando `last_modified` es igual (se probó mandando un `progress` deliberadamente distinto con el mismo timestamp — se ignoró correctamente, sin snapshot nuevo); borrado de ambos archivos del inbox tras cada poll, en todos los casos. `./gradlew compileJava` compila limpio.
  - De paso: corregido `.gitignore` (tenía `backend/data/`, el inbox real vive bajo `backend/bootstrap/data/` — mismo bug de ruta que documentaba, ahora también corregido, `docs/SYNC_PROTOCOL.md`).
- **Documentación**: `docs/SYNC_PROTOCOL.md`, `docs/DEPLOYMENT.md`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`, `docs/ROADMAP.md` actualizados a reflejar el protocolo nuevo.
- **Skill global `senior-backend-dev`**: actualizado a Spring Data JPA como default (sesión anterior, sin cambios hoy).
- **`nolost`** (repo hermano): `ROADMAP.md`/`PROGRESS.md` en formato estándar (sesión anterior, sin cambios hoy).

## En progreso

Nada.

## Siguiente

En orden recomendado:

1. **Pruebas unitarias** de `SyncPayloadParser.parseBatch` (batch mixto válido/inválido) y `ProjectSyncService` (creación, actualización, no-op por `last_modified`) — marcadas sin verificar en `ROADMAP.md` Etapas 1 y 2, es lo único que queda de deuda técnica real en el código nuevo.
2. **Verificación visual en navegador** del dashboard contra el mockup "OpenClaw Control Center" (Etapa 4) — hasta ahora solo verificado por `curl`/build.
3. **Desplegar `agent-project` en `nolost-vps`** siguiendo `docs/DEPLOYMENT.md` (requiere acceso SSH al servidor, fuera del alcance de esta sesión).
4. **Configurar OpenClaw** (máquina del usuario) para que barra `docs/` de cada proyecto registrado y genere/envíe `progreso.json`/`nuevo.json` según el contrato de `docs/SYNC_PROTOCOL.md` — es lo único que falta para que el dashboard reciba datos reales en vez de los de prueba usados hoy.

## Bloqueadores

Ninguno técnico. El deploy real depende de acceso al servidor `nolost-vps`, fuera del alcance de esta sesión.

## Última actualización

2026-08-19
