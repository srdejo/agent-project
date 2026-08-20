# PROGRESS.md

Estado del proyecto a **2026-08-19**. Actualizar este archivo cada vez que se cierre una tarea del ROADMAP — no dejarlo desactualizado.

## Estado actual

Etapa 5 — Sync recurrente + deploy a `nolost-vps` (🟡 protocolo de sync rediseñado y verificado en local; **ya desplegado y en vivo** en `https://agent.srdejo.com.co`; solo falta que OpenClaw genere/envíe los archivos de verdad).

## Tarea actual

Ninguna en ejecución. Se cerró el rediseño del protocolo de sync y el deploy real a `nolost-vps`.

## Completado

- **Backend — store y API** (Etapas 2–3): Spring Data JPA + Hibernate, esquema Flyway (`projects`, `project_snapshots`). `GET /api/projects`, `GET /api/projects/{id}`. **Verificado** con `curl`.
- **Frontend — dashboard** (Etapa 4): `ProjectApiService` contra la API real, vistas de listado y detalle. **Verificado**: build + `curl` contra el proxy; falta verificación visual en navegador real (pendiente, ver "Siguiente").
- **Backend — protocolo de sync rediseñado** (Etapa 1 + 5, 2026-08-19): reemplazado el modelo de un JSON por proyecto por dos archivos fijos agregados por id, `progreso.json` (proyectos existentes) y `nuevo.json` (proyectos nuevos). Motivo y detalle en `docs/DECISIONS.md`; contrato completo en `docs/SYNC_PROTOCOL.md`.
  - `modules:parser` — `SyncPayloadParser.parseBatch()` valida cada entrada del mapa de forma independiente (una entrada mala no bloquea al resto); `SyncPayload` gana `lastModified`.
  - `modules:projects` — `ProjectSyncPort` gana `exists(id)`; `ProjectSyncRequest`/`ProjectEntity`/`ProjectSyncService` usan `source_last_modified` (migración `V2__project_last_modified.sql`) en vez del hash SHA-256 anterior para decidir si hay que actualizar.
  - `modules:progress` — `InboxSyncJob` reescrito: busca `progreso.json`/`nuevo.json`, aplica las reglas de validación por archivo (progreso = solo ids existentes, nuevo = solo ids nuevos), y **borra ambos archivos siempre al terminar**, haya habido cambios o no.
  - **Verificado end-to-end en local** (Postgres 17 + nginx del entorno `infra/` de `srdejo`, backend real con `bootRun`, vía `curl`): creación de proyecto nuevo vía `nuevo.json`; rechazo de un id desconocido en `progreso.json` (no crea, solo loguea warning); actualización de un proyecto existente con `last_modified` distinto (genera nuevo snapshot); no-op cuando `last_modified` es igual (se probó mandando un `progress` deliberadamente distinto con el mismo timestamp — se ignoró correctamente, sin snapshot nuevo); borrado de ambos archivos del inbox tras cada ciclo, en todos los casos. `./gradlew compileJava` compila limpio.
  - De paso: corregido `.gitignore` (tenía `backend/data/`, el inbox real vive bajo `backend/bootstrap/data/` — mismo bug de ruta que documentaba, ahora también corregido, `docs/SYNC_PROTOCOL.md`).
- **Backend — sync de polling a watcher reactivo** (2026-08-19): `InboxSyncJob` migrado de `@Scheduled(fixedDelayString = poll-interval-ms)` a `java.nio.file.WatchService` sobre `inboxDir` (`ENTRY_CREATE`/`ENTRY_MODIFY`), corriendo en un virtual thread iniciado en `@PostConstruct` y detenido en `@PreDestroy`. Debounce de 400ms para coalescer `progreso.json`+`nuevo.json` en un solo `SyncRunPort.record()` cuando llegan casi juntos (mismo `RunAccumulator`, como antes). Si `key.reset()` da `false` (directorio inválido), re-registra el watch en vez de morir. Propiedad `agent-project.sync.poll-interval-ms` eliminada de `application.yml`.
  - **Verificado end-to-end en local** (Postgres 17, `bootRun`, vía `curl` y logs): con la app corriendo, se soltaron `nuevo.json` + `progreso.json` casi simultáneos (patrón `.tmp` + rename atómico, igual que en prod) — ambos se procesaron en el mismo ciclo de debounce (~700ms después del drop, mismo hilo `x-sync-debounce`, un solo log de cada), sin esperar ningún intervalo fijo. Un segundo drop posterior con id inexistente en `progreso.json` confirmó que el watch sigue vivo tras el primer ciclo y que el rechazo funciona igual que antes. Proyecto de prueba (`watchtest`) creado, confirmado vía `GET /api/projects`, y limpiado de la base al terminar. `docs/SYNC_PROTOCOL.md`, `docs/DEPLOYMENT.md`, `docs/ROADMAP.md` actualizados para reflejar que ya no hay polling.
- **Documentación**: `docs/SYNC_PROTOCOL.md`, `docs/DEPLOYMENT.md`, `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`, `docs/ROADMAP.md` actualizados a reflejar el protocolo nuevo.
- **Skill global `senior-backend-dev`**: actualizado a Spring Data JPA como default (sesión anterior, sin cambios hoy).
- **`nolost`** (repo hermano): `ROADMAP.md`/`PROGRESS.md` en formato estándar (sesión anterior, sin cambios hoy).
- **Frontend — mobile-first (Etapa 6, 2026-08-19)**: sin acceso a navegador/DevTools en esta sesión, se identificó por inspección directa del CSS/Tailwind qué se rompía en viewport de 375px y se corrigió:
  - `project-list.html` — la fila de proyecto usaba `grid-cols-[minmax(140px,1fr)_minmax(90px,130px)_minmax(64px,110px)_60px]`, cuyo ancho mínimo (~410px + gaps) supera un viewport de 375px y forzaba overflow horizontal. Ahora es `grid-cols-2` en mobile (nombre a la izquierda, % a la derecha en la primera fila; barra de progreso a ancho completo en la segunda fila) y vuelve al grid original de 4 columnas desde `sm:`. El sparkline SVG se oculta en mobile (`hidden sm:block`) porque a ese ancho no aporta y competía por espacio.
  - `project-detail.html` — título y porcentaje con tamaños responsivos (`text-2xl sm:text-4xl` / `text-3xl sm:text-5xl` en vez de fijos `text-4xl`/`text-5xl`), meta-línea (repo/etapa/estado) con `flex-wrap` y separadores `·` ocultos en mobile. El resto de las grillas (historial, completado/siguiente/bloqueado, evidencia, actividad) ya eran `grid-cols-1` por defecto y no necesitaron cambio.
  - `app.html` (header global) — padding/gaps/tamaños de fuente reducidos en mobile, "Project Control Center" con `truncate` para que no empuje el bloque "PUBLIC TRACKING" fuera del viewport.
  - **Verificado**: `npm run build` compila sin errores. Desplegado a `nolost-vps` (build de producción subido a `/home/srdejo/agent-project-frontend`, nginx reiniciado, `200` en `/` y `/api/projects`). El usuario confirmó visualmente contra `https://agent.srdejo.com.co` desde su iPhone (capturas del listado de proyectos): stats en grid 2x2, filas sin overflow horizontal, progreso/porcentaje legibles — "quedó súper bien". **Etapa 6 cerrada (🟢)** en `ROADMAP.md`; de paso también se cerró el pendiente de verificación visual de la Etapa 4.

## Desplegado (2026-08-19)

`agent-project` está en vivo en `nolost-vps`:
- Backend: `agent-project.service` (systemd), puerto `8083` loopback (ver `PORTS.md` en la raíz del workspace — próximo puerto libre: `8084`), `EnvironmentFile=/home/srdejo/agent-project/.env`, Postgres propio (`agent_project`, usuario y base creados en esta sesión).
- Frontend: build de producción en `/home/srdejo/agent-project-frontend`, servido por nginx.
- Dominio: `https://agent.srdejo.com.co` (DNS A creado por el usuario, certificado Let's Encrypt vía `certbot --nginx`, redirect HTTP→HTTPS `301`).
- Flyway aplicó `V1`+`V2` limpio contra la base nueva del servidor.
- **Verificado**: `curl` contra `/` y `/api/projects` en HTTP y HTTPS, `200` en ambos.

## En progreso

Nada. El roadmap documentado está 100% completado y verificado.

## Siguiente

Definir con el usuario qué sigue: nuevas features del dashboard, mejoras al sync, o dar por cerrado el proyecto.

## Completado (adición 2026-08-19)

- **Pruebas unitarias verificadas** (Etapas 1 y 2, 2026-08-19): `SyncPayloadParserTest` (8 tests) y `ProjectSyncServiceTest` (4 tests) — escritas en commit `725261a`, corridas con `./gradlew :modules:parser:test :modules:projects:test` en verde (BUILD SUCCESSFUL in 8s). Cubren: parsing de entry válida completa, normalización de opcionales ausentes, rechazo de entry sin campo requerido sin bloquear las demás, `progress` fuera de rango, `status` inválido, `tasks[].status` inválido, `last_modified` no ISO-8601, JSON raíz no-objeto, JSON inválido (parser); creación, actualización por `last_modified` distinto, no-op por `last_modified` igual, `exists()` (service).
- **Sync automático vía OpenClaw** configurado y en producción: barre `docs/` de los 9 proyectos, genera `progreso.json`/`nuevo.json`, los sube al inbox del VPS sin reiniciar el servicio. Corre a las 8am/12pm/6pm y bajo demanda.

## Bloqueadores

Ninguno.

## Última actualización

2026-08-19
