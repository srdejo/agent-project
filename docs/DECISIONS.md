# DECISIONS.md

Decisiones técnicas tomadas hasta ahora, con el motivo. Solo se listan decisiones confirmadas explícitamente por el usuario o deducibles sin ambigüedad del contexto — no se inventan justificaciones.

## Alcance y proceso

### Replicar la estructura de `consulting` / `distriapp`
El usuario pidió explícitamente que `agent-project` tuviera `docs/`, `backend/` y `frontend/` "similar a consulting o distri-app". Se replicó el mismo stack (Gradle multi-módulo + Angular standalone + Tailwind) y las mismas convenciones de gobernanza (`CLAUDE.md` + `docs/ARCHITECTURE.md` + `docs/DECISIONS.md` + `docs/ROADMAP.md` + `docs/PROGRESS.md`) por consistencia entre los repos del mismo autor.

### Alcance de la primera pasada: scaffolding vacío, sin lógica de negocio
Se preguntó explícitamente al usuario entre tres alcances (scaffolding vacío, scaffolding + primer módulo funcional, o solo carpetas sin config). Eligió **scaffolding vacío** — estructura completa de Gradle/Angular lista para compilar, pero sin implementar el parser de Markdown ni la API todavía.

### Módulos de dominio del backend: `projects`, `parser`, `progress`
Se preguntó explícitamente al usuario qué división de módulos usar (equivalente a `ingestion`/`customers`/... en `distriapp`). Eligió esta división de tres módulos en vez de un único módulo `core` o dejarlo indefinido.

### Sin módulo `platform:tenancy` ni `platform:security` todavía
A diferencia de `consulting`/`distriapp` (multi-tenant, con login), `agent-project` no tiene definido aún si será multi-usuario o con autenticación — el README del estándar solo describe "proyectos públicos y privados" como visión a futuro (Fase 6). Se omitieron esos módulos platform por YAGNI hasta que haya una decisión de producto explícita sobre auth/multi-tenencia. **No confirmado con el usuario** — revisar antes de construir cualquier feature que dependa de usuarios o visibilidad pública/privada.

## Backend

### Java 21 + Spring Boot 3.4.1, Gradle multi-módulo (Groovy DSL), **Spring Data JPA** (no JdbcTemplate)
`consulting`/`distriapp`/`hotel` usan JdbcTemplate sin JPA — el usuario aclaró explícitamente que esa fue una decisión puntual de esos repos, no una regla permanente, y pidió que de aquí en adelante el default sea Spring Data JPA + Hibernate. `agent-project` es el primer proyecto construido con esta convención; los repos anteriores no se migran retroactivamente. El skill global `senior-backend-dev` (`C:\Users\Daniel\.claude\skills\senior-backend-dev\`) se actualizó en la misma sesión para reflejar este cambio como default (`references/spring-data-jpa.md` reemplaza a `references/jdbc-template.md` como referencia principal; este último se conserva sin borrar por si un proyecto futuro pide JdbcTemplate explícitamente).

### `agent-project` sí tiene base de datos propia (Postgres), el archivo de sync no la reemplaza
En una iteración temprana de esta sesión se consideró que el backend persistiera en archivos JSON planos en vez de una base de datos ("no quiero base de datos sino un archivo"). El usuario luego lo repensó: **sí quiere Postgres** como store real (`projects`, `project_snapshots`); lo que no quiere es que el mecanismo de *entrada* del sync (el JSON que deposita OpenClaw) tenga que ser tratado como si fuera la base de datos. El archivo de sync es solo el formato de entrada — `InboxSyncJob` lo valida y lo aplica sobre las tablas Postgres vía JPA.

### Sync por archivo (inbox + polling), no por endpoint HTTP público
El usuario prefirió explícitamente que la sincronización sea "vía file" — OpenClaw deposita un JSON en el servidor (scp) y el backend lo revisa periódicamente — en vez de exponer un `POST /api/projects/sync` público. Ver `docs/SYNC_PROTOCOL.md` para el contrato completo y `InboxSyncJob` (`modules:progress`) para la implementación. No hay autenticación en el path de escritura porque no existe: el único mecanismo de entrada es el filesystem del servidor, no HTTP.

### Un proyecto nuevo se crea automáticamente si el JSON trae un `id` desconocido
Pedido explícito del usuario: el archivo de sync debe poder registrar un proyecto que el backend no conocía aún, sin un paso de registro manual previo. `ProjectSyncService.applySync` hace upsert — si el `id` no existe en `projects`, lo crea.

### Protocolo de sync rediseñado: `progreso.json` + `nuevo.json` (mapa por id), reemplaza el archivo-por-proyecto
Pedido explícito del usuario, antes de que este mecanismo llegara a producción (confirmado en `PROGRESS.md`: Etapa 5 sin desplegar, OpenClaw sin configurar todavía — sin costo de migración real). El modelo pasa de "un JSON por proyecto, nombrado `<id>.json`" a **dos archivos fijos**, cada uno un mapa `id -> datos`, que OpenClaw regenera completos en cada corrida tras barrer la carpeta `docs/` de todos los proyectos:
- `progreso.json` — solo proyectos que ya existen; cada entrada trae su propio `last_modified` (metadata del estado, no de la subida) que el backend compara contra el guardado para decidir si actualiza o no.
- `nuevo.json` — solo proyectos que no existen todavía, con los mismos campos requeridos que antes para crearlos.

Consecuencias de diseño:
- **Validación por entrada, no por archivo**: una entrada inválida (o un id que no corresponde al archivo — ej. un id desconocido en `progreso.json`) se descarta con warning, no rechaza el archivo completo. Antes, un solo campo mal formado invalidaba todo el JSON.
- **El id ya no va como campo dentro del objeto** — es la clave del mapa.
- **Reversión deliberada del principio "nunca borrar en silencio"** (`InboxSyncJob` antes movía cada archivo procesado a `processed/`/`rejected/`, nunca lo borraba). Ahora `progreso.json`/`nuevo.json` se borran siempre tras cada poll, haya habido cambios o no — porque OpenClaw los regenera frescos en su próximo ciclo y el histórico real ya vive en `project_snapshots`, no hace falta conservar los archivos de entrada.
- **El mecanismo de "¿cambió o no?" pasa de un hash de contenido calculado por el job (`last_sync_hash`, SHA-256) a la metadata `last_modified` que trae el propio payload** (columna `projects.source_last_modified`, migración `V2__project_last_modified.sql`) — se elimina la duplicidad de tener dos mecanismos de detección de cambio.

## Frontend

### Angular 22 standalone + Tailwind, sin librería de componentes
Mismo stack que `consulting` y `distriapp`. Sin auth ni layout todavía — se construirán cuando exista una decisión de producto sobre si el dashboard requiere login.

### Diseño visual importado desde Claude Design: "OpenClaw Control Center"
El usuario compartió el link a un proyecto de Claude Design (`41d19e75-a961-46aa-9e34-b7b2c3b58530`, tipo `PROJECT_TYPE_PROJECT`, no `DESIGN_SYSTEM`). A diferencia de sesiones anteriores en `distriapp`/`hotel`, aquí sí fue posible leerlo con `DesignSync` (`list_files`/`get_file`) tras el login explícito del usuario (`/design-login`) — `list_projects` sigue sin listar proyectos que no son de tipo design-system, pero `get_project`/`list_files`/`get_file` funcionan si se pasa el `projectId` directamente. Se implementó fielmente el mockup `OpenClaw Control Center.dc.html`: paleta (`#F4F1EA` fondo, `#14120E` tinta, tarjetas `#FAF8F3`, acento `#FACC15`, estados verde/naranja/azul), tipografía Space Grotesk + JetBrains Mono, vista global (stats + lista de proyectos + actividad del agente) y vista de detalle (historial de progreso, completado/siguiente/bloqueado, evidencia de verificación, actividad). El spec funcional que acompañaba el mockup (`uploads/OPENCLAW_PROJECT_CONTROL_CENTER.md`) se guardó en `docs/OPENCLAW_PROJECT_CONTROL_CENTER.md` — es más detallado que el `README.md` raíz del estándar y es la referencia de producto para las Etapas 1–4 de `ROADMAP.md`.

### Datos del dashboard: API real (`ProjectApiService`), ya no mock
El frontend inicialmente usó 4 proyectos de ejemplo en memoria (`ProjectMockService`) para poder construir y verificar la UI sin bloquear en el backend. Una vez el backend quedó funcional (Postgres + JPA + `InboxSyncJob`), se reemplazó por `ProjectApiService` (HttpClient) contra `GET /api/projects`/`GET /api/projects/{id}`, y se borró `ProjectMockService`. Verificado con un proyecto sembrado manualmente en el inbox local.

## Módulos de negocio (actualizado tras implementar)

### `modules:parser` ya no parsea Markdown — valida el JSON de sync
La decisión original (`ROADMAP.md` Etapa 1) era que este módulo leyera `ROADMAP.md`/`TASKS.md`/`PROGRESS.md` directamente. Quedó obsoleta cuando se definió que OpenClaw hace esa lectura en la máquina del desarrollador y envía el resultado como JSON — el parseo de Markdown nunca ocurre en el servidor. `modules:parser` se reproponer para validar/normalizar ese JSON (`SyncPayloadParser`) antes de aplicarlo.

### `modules:projects` no depende de `modules:parser`
Para no acoplar el módulo de persistencia al formato de entrada del sync, `modules:projects` expone su propio contrato (`api/ProjectSyncRequest`, `api/ProjectSyncPort`) en vez de depender del `SyncPayload` de `modules:parser`. Es `modules:progress` quien conoce ambos módulos y mapea de uno a otro en `InboxSyncJob`.
