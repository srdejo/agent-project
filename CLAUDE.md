# CLAUDE.md

Reglas de trabajo para este repo (Agent Project — implementación del parser/dashboard descrito en `README.md`). Léelo antes de tocar código.

## Qué es este proyecto

`README.md` (raíz) define el **estándar** Agent Project: cómo un proyecto asistido por IA describe su roadmap, tareas y progreso mediante `ROADMAP.md`/`TASKS.md`/`PROGRESS.md`/`AGENTS.md`. Este repo (`backend/` + `frontend/`) es la **implementación de referencia**: recibe (vía `docs/SYNC_PROTOCOL.md`) el estado ya parseado de cualquier proyecto registrado — el parseo de esos archivos ocurre en la máquina del desarrollador (OpenClaw), no en este servidor — y expone un dashboard que visualiza el resultado.

Para el estado real del código, decisiones tomadas y qué falta, ver (en ese orden de lectura):
1. `docs/ARCHITECTURE.md` — cómo está construido hoy, no aspiracional.
2. `docs/DECISIONS.md` — por qué se construyó así, qué alternativas se descartaron.
3. `docs/ROADMAP.md` — etapas hacia la primera versión funcional, con checkboxes que solo se marcan cuando algo está verificado (compila/corre/pasa), no cuando "se escribió".
4. `docs/PROGRESS.md` — snapshot del estado actual y próximo paso recomendado. Actualízalo cada vez que cierres una tarea del roadmap.

## Stack

- **Backend**: `backend/` — Java 21, Spring Boot 3.4.x, Gradle multi-módulo (Groovy DSL), **Spring Data JPA + Hibernate**, Flyway, PostgreSQL. `agent-project` es el primer proyecto del autor construido con esta convención (ver `docs/DECISIONS.md`) — `consulting`/`distriapp`/`hotel` usan JdbcTemplate, decisión puntual de esos repos que no se migra retroactivamente.
- **Frontend**: `frontend/` — Angular 22, standalone components, signals, `@if`/`@for`, Tailwind CSS.
- Sin endpoint de escritura público: los datos entran vía un job que lee archivos JSON de un inbox en disco (`docs/SYNC_PROTOCOL.md`), no vía HTTP.

## Convenciones de código

- Todo el código (paquetes, clases, campos, tablas/columnas) en **inglés**.
- Backend: capas `web` (controller/DTO) → `service` → `repository` (`JpaRepository`) → `model` (`@Entity`) dentro de cada módulo de `modules/*`. La entidad JPA nunca sale del módulo — se mapea a DTO en `web`/`service`. Ver el skill `senior-backend-dev` para los patrones completos (`references/spring-data-jpa.md`).
- `modules/projects` y `modules/parser` no dependen de otros módulos de negocio entre sí. `modules/progress` depende de ambos (aplica el sync validado por `parser` sobre `projects` vía `ProjectSyncPort`) — es la única dependencia cruzada permitida hoy; cualquier otra debe documentarse en `ARCHITECTURE.md`.
- Sin comentarios que expliquen QUÉ hace el código. Comentarios solo para el PORQUÉ no obvio.
- No añadir abstracciones, validaciones o manejo de errores para escenarios que no pueden ocurrir. YAGNI.
- Frontend: componentes standalone, lazy-loaded por feature, signals para estado local, sin `NgModule`.

## Proceso de trabajo

- Antes de escribir código nuevo, leer `docs/PROGRESS.md` (próximo paso recomendado) y `docs/ROADMAP.md` (etapa actual) para no duplicar ni desordenar el orden de dependencias.
- Al cerrar una tarea: actualizar `docs/PROGRESS.md` y marcar el checkbox correspondiente en `docs/ROADMAP.md` **solo si fue verificado** (build/test/llamada real), no por haber sido escrito.
- Decisiones de producto o arquitectura que impliquen descartar una alternativa razonable: preguntar al usuario si no hay una instrucción explícita previa, y registrar la decisión (con el motivo dado) en `docs/DECISIONS.md`.
- No inventar alcance no pedido.
