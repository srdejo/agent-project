# ARCHITECTURE.md

Descripción de la arquitectura tal como existe hoy en el código (no aspiracional). Para el plan de qué falta, ver `ROADMAP.md`. Para el porqué de cada decisión, ver `DECISIONS.md`.

## Vista general

Monorepo con dos proyectos desplegables por separado, siguiendo la misma estructura que los repos hermanos `consulting`, `distriapp` y `hotel` del mismo autor:

```
agent-project/
  backend/    API Spring Boot, monolito modular (Spring Data JPA + Hibernate, Postgres)
  frontend/   SPA Angular (standalone components), conectada a la API real
  docs/       Documentación de gobernanza del propio repo
  deploy.ps1  Script de despliegue a nolost-vps
```

`agent-project` es una implementación del estándar descrito en `README.md` raíz: un backend que recibe actualizaciones de progreso de otros proyectos y un dashboard ("OpenClaw Project Control Center", ver `docs/OPENCLAW_PROJECT_CONTROL_CENTER.md`) que las visualiza. El propio `agent-project` es el **primer** proyecto construido con la convención de acceso a datos Spring Data JPA (ver `DECISIONS.md`) — a diferencia de `consulting`/`distriapp`/`hotel`, que usan JdbcTemplate.

## Cómo entran los datos: sync por archivo, no por API pública

El backend **no** clona repos ni parsea Markdown. Un agente externo (OpenClaw, corriendo en la máquina del desarrollador) deposita dos archivos fijos (`progreso.json`, `nuevo.json` — mapas `id -> datos`, no uno por proyecto) en un directorio "inbox" del servidor; un job programado dentro del backend los lee, valida y aplica entrada por entrada, y los borra al terminar. Ver `docs/SYNC_PROTOCOL.md` para el contrato completo.

```
OpenClaw (máquina del dev) --scp--> data/inbox/{progreso,nuevo}.json --poll (InboxSyncJob)--> Postgres (projects, project_snapshots)
                                                                                        │
                                                                                        ▼
                                                              GET /api/projects, GET /api/projects/{id}
                                                                                        │
                                                                                        ▼
                                                                              Frontend (dashboard)
```

No existe un endpoint HTTP de escritura pública — el único mecanismo de entrada es el archivo en disco, que el proceso del backend controla.

## Backend

Gradle multi-módulo (Groovy DSL), Java 21, Spring Boot 3.4.1, **Spring Data JPA + Hibernate**, Flyway, PostgreSQL.

```
backend/
  bootstrap/            módulo ejecutable (main class, application.yml, migraciones Flyway)
  platform/
    web-common/          manejo de errores y utilidades web compartidas (vacío por ahora)
  modules/
    projects/            @Entity ProjectEntity/ProjectSnapshotEntity + repos JPA + API pública (GET /api/projects, GET /api/projects/{id}, ProjectSyncPort)
    parser/               valida y normaliza el JSON de sync (SyncPayloadParser) — no parsea Markdown, eso lo hace OpenClaw
    progress/             InboxSyncJob (@Scheduled) — escanea el inbox y aplica los cambios vía ProjectSyncPort
```

Reglas de dependencia entre módulos:
- `modules/projects` no depende de ningún otro módulo de negocio — su `ProjectEntity` nunca sale del módulo, solo DTOs vía `web/` y el contrato `api/ProjectSyncPort` para quien necesite aplicar un sync.
- `modules/parser` no depende de otros módulos de negocio.
- `modules/progress` depende de `modules/projects` (para aplicar el sync) y `modules/parser` (para validar el JSON) — es la única dependencia cruzada entre módulos de negocio hoy.
- `bootstrap` depende de todos, habilita `@EnableJpaRepositories`/`@EntityScan`/`@EnableScheduling` con `basePackages = "co.com.srdejo.agentproject"` (necesario porque los módulos no son subpaquetes del paquete de `bootstrap`).

Esquema Postgres (`V1__init_schema.sql` + `V2__project_last_modified.sql`): tablas `projects` (estado actual, con `completed`/`next_tasks`/`blocked`/`checks`/`events` como columnas `JSONB` mapeadas con `@JdbcTypeCode(SqlTypes.JSON)` — son listas cortas que siempre se reescriben completas en cada sync, no se normalizaron a tablas aparte; `source_last_modified` guarda la metadata que trae cada entrada del sync, usada para decidir si hay que actualizar) y `project_snapshots` (historial de progreso para el sparkline y el gráfico de detalle).

## Frontend

Angular 22, standalone components, sin `NgModule`, Tailwind CSS. Paleta y tipografía (Space Grotesk + JetBrains Mono) importadas del mockup "OpenClaw Control Center" de Claude Design.

```
frontend/src/app/
  core/
    models/project.ts        ProjectSummary, ProjectDetail, ProjectListResponse (shape que devuelve el backend)
    services/project-api.service.ts   HttpClient contra /api/projects (proxeado a localhost:8080 en dev vía proxy.conf.json)
  features/
    projects/project-list.ts   vista global: stats + lista de proyectos + actividad del agente
    projects/project-detail.ts  vista de detalle: historial de progreso, completado/siguiente/bloqueado, evidencia, actividad
  layout/               (vacío) — shell de la app cuando exista navegación adicional
  shared/format/progress-chart.ts   utilidades de color/sparkline/línea de historial, compartidas entre ambas vistas
```

Ya no usa datos mock (`ProjectMockService` fue eliminado) — ambas vistas consumen `GET /api/projects` y `GET /api/projects/{id}` en tiempo real.

## Cómo correrlo

- Backend: `cd backend && docker-compose up -d && ./gradlew bootRun` (requiere Postgres local — `docker-compose.yml` en `backend/`). Nota: el directorio de trabajo de `bootRun` es `backend/bootstrap/`, así que `./data/inbox` (default) se resuelve ahí salvo que se fije `SYNC_INBOX_DIR` explícitamente.
- Frontend: `cd frontend && npm install && npm start` (proxy a `localhost:8080/api` vía `proxy.conf.json`).

Verificado en esta sesión: `./gradlew build` compila, `bootRun` levanta contra Postgres local, un JSON de ejemplo depositado en el inbox se sincroniza y aparece en `GET /api/projects`/`GET /api/projects/{id}`, y el frontend (`npm run build` + `npm start`) sirve y consume esos mismos endpoints vía el proxy — verificado por `curl`, no visualmente en un navegador real.

## Despliegue

Ver `docs/DEPLOYMENT.md` (systemd, nginx, estructura de directorios en `nolost-vps`) y `deploy.ps1` en la raíz del repo. No ejecutado en esta sesión — sin acceso SSH al servidor.
