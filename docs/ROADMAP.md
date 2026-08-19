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

- [x] Documentar el esquema JSON en `docs/SYNC_PROTOCOL.md` (rediseñado 2026-08-19: `progreso.json`/`nuevo.json` agregados por id, en vez de un archivo por proyecto — ver `docs/DECISIONS.md`).
- [x] `SyncPayloadParser.parseBatch` (`modules:parser`) — valida campos requeridos, enums, `last_modified`, normaliza opcionales; una entrada inválida no bloquea al resto del archivo.
- [ ] Pruebas unitarias de `SyncPayloadParser.parseBatch` contra payloads de ejemplo (válidos e inválidos, batch mixto).

## Etapa 2 — Store de proyectos (Postgres + JPA) 🟢

- [x] Esquema Flyway (`projects`, `project_snapshots`).
- [x] `ProjectEntity`/`ProjectSnapshotEntity` + `ProjectJpaRepository`/`ProjectSnapshotJpaRepository`.
- [x] `ProjectSyncService` (upsert, crea el proyecto si no existía; dedupe por `source_last_modified` en vez de hash SHA-256 desde el rediseño del protocolo).
- [ ] Pruebas unitarias/`@DataJpaTest` de `ProjectSyncService` (creación, actualización, no-op si `last_modified` no cambió).

## Etapa 3 — API de progreso 🟢

- [x] `GET /api/projects` — lista con stats agregados (count, avg, blocked, verified) y `series` por proyecto.
- [x] `GET /api/projects/{id}` — detalle completo + historial de snapshots.
- [x] Verificado con `curl` contra un proyecto sembrado manualmente.

## Etapa 4 — Dashboard 🟢

**Objetivo**: visualización descrita en `docs/OPENCLAW_PROJECT_CONTROL_CENTER.md` (mockup "OpenClaw Control Center" importado de Claude Design).

- [x] Vista de listado multi-proyecto (stats globales + lista + actividad del agente).
- [x] Vista de detalle de un proyecto (historial de progreso, completado/siguiente/bloqueado, evidencia de verificación, actividad).
- [x] Conectado a la API real (`ProjectApiService`, `ProjectMockService` eliminado).
- [x] Verificar visualmente en navegador contra el mockup original — confirmado por el usuario contra `https://agent.srdejo.com.co` real (ver Etapa 6, 2026-08-19).

## Etapa 5 — Sync recurrente + deploy a `nolost-vps` 🟡

**Objetivo**: que el dashboard, corriendo en `nolost-vps`, se mantenga actualizado con el avance real de `consulting`, `distriapp`, `hotel`, `nolost` y `agent-project`.

- [x] `InboxSyncJob` (`modules:progress`, `@Scheduled`, cada `SYNC_POLL_INTERVAL_MS` — default 6 horas) — busca `progreso.json`/`nuevo.json`, valida y aplica entrada por entrada, borra ambos archivos siempre al terminar.
- [x] Verificado localmente con el protocolo nuevo (2026-08-19): creación vía `nuevo.json`, rechazo de id desconocido en `progreso.json`, actualización con `last_modified` distinto, no-op con `last_modified` igual (probado enviando un `progress` distinto con el mismo timestamp — se ignoró correctamente), borrado de ambos archivos tras cada poll. Todo verificado por `curl` contra el backend local.
- [x] `deploy.ps1` (build + scp + restart de `agent-project`/nginx en `nolost-vps`).
- [x] `docs/DEPLOYMENT.md` (systemd, nginx, estructura de directorios esperada en el VPS).
- [x] Desplegado en `nolost-vps`: backend (`agent-project.service`, puerto 8083 loopback, Postgres `agent_project` propio) + frontend estático vía nginx. Dominio `https://agent.srdejo.com.co` con certificado Let's Encrypt (certbot). Verificado con `curl` contra `/` y `/api/projects` en HTTP y HTTPS — `200` en ambos, redirect `301` de HTTP a HTTPS.
- [ ] `nolost` migrado a `ROADMAP.md`/`PROGRESS.md` con checkboxes (hecho — ver el repo `nolost`), pero falta que OpenClaw efectivamente genere y envíe su primer JSON de sync.
- [ ] Configurar y programar OpenClaw (en la máquina del usuario) para generar y enviar el JSON de cada proyecto — fuera del alcance de este repo, ver nota en `docs/SYNC_PROTOCOL.md`.

## Etapa 6 — Ajustes de diseño mobile-first del dashboard 🟢

**Objetivo**: que el dashboard (`https://agent.srdejo.com.co`) se vea y use bien en móvil — hoy solo se verificó build + `curl`, nunca se probó en pantalla chica ni se diseñó pensando en eso. Etapa agregada a pedido del usuario (2026-08-19).

- [x] Revisar el CSS/breakpoints existentes (sin navegador real disponible en esta sesión) y ubicar por inspección lo que se rompe: en `project-list.html` la fila de proyecto usaba `grid-cols-[minmax(140px,1fr)_minmax(90px,130px)_minmax(64px,110px)_60px]` con un ancho mínimo (~410px + gaps) mayor al viewport de un iPhone (375px), forzando overflow horizontal; el header de `project-detail.html` usaba `text-4xl`/`text-5xl` fijos sin reducir en mobile; el header global (`app.html`) tenía poco margen entre "Project Control Center" y "PUBLIC TRACKING" en 375px.
- [x] Ajustar layout de la vista de listado (`project-list.html`): fila de proyecto pasa a `grid-cols-2` en mobile (nombre + % arriba, barra de progreso ocupando el ancho completo debajo, sparkline SVG oculto con `hidden sm:block`) y vuelve al grid original de 4 columnas desde `sm:`.
- [x] Ajustar layout de la vista de detalle (`project-detail.html`): título y % de progreso con tamaños de fuente responsivos (`text-2xl sm:text-4xl`, `text-3xl sm:text-5xl`), meta-línea (repo/etapa/estado) con `flex-wrap` y separadores `·` ocultos en mobile. El resto de las grillas (historial, completado/siguiente/bloqueado, evidencia, actividad) ya usaban `grid-cols-1` por defecto y no requirieron cambios.
- [x] Revisar navegación/header (`app.html`) para pantallas chicas: padding, gaps y tamaños de fuente reducidos en mobile, "Project Control Center" con `truncate` para evitar que empuje el bloque de la derecha fuera del viewport.
- [x] Verificar en navegador móvil real: desplegado a `nolost-vps` (build de producción, nginx reiniciado, `200` en `/` y `/api/projects`) y confirmado por el usuario contra `https://agent.srdejo.com.co` en su iPhone real (2026-08-19, capturas en el listado de proyectos) — stats en grid 2x2, filas de proyecto sin overflow horizontal, progreso/porcentaje legibles.
