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

- **Backend**: `backend/`, sobre el arquetipo hexagonal reactivo `powerup-arquetipo-rx` (`D:\Workspace Daniel\02-sandbox\pragma\powerup-arquetipo-rx`). Un solo módulo Gradle, Java 26, Spring Boot 4.1, **WebFlux + Spring Data R2DBC**, Flyway (con conexión JDBC propia), PostgreSQL, MapStruct, Lombok, springdoc (`/swagger-ui.html`) y JaCoCo. Es una **excepción** registrada a la convención "Spring Data JPA por defecto" del autor (ver `docs/DECISIONS.md`).
- **Frontend**: `frontend/` — Angular 22, standalone components, signals, `@if`/`@for`, Tailwind CSS.
- Sin endpoint de escritura: los datos entran por un job que lee `snapshot.json` de un inbox en disco (`docs/SYNC_PROTOCOL.md`), no por HTTP. Todo el sitio está detrás de oauth2-proxy (login de Google).

## Convenciones de código

- Todo el código (paquetes, clases, campos, tablas/columnas) en **inglés**.
- Backend: capas del arquetipo, con las dependencias apuntando hacia adentro. `domain` (`model`, `api` = puertos de entrada `I…ServicePort`, `spi` = puertos de salida `I…PersistencePort`, `usecase`) es Java puro más `reactor-core` y Lombok, sin Spring ni R2DBC. `application` (`handler`, `dto`, `mapper` con MapStruct) convierte modelo de dominio en DTO. `infrastructure` contiene los adaptadores (`input/rest`, `input/inbox`, `input/websocket`, `out/r2dbc`, `out/agent`) y la configuración: `BeanConfiguration` conecta puertos y adaptadores, y los use cases no llevan anotaciones de Spring. Las entidades R2DBC nunca salen de `infrastructure/out/r2dbc`.
- La regla de avance vive solo en `ProgressCalculator` (`domain/usecase`). Si cambia, sube `RULE_VERSION` para que los proyectos se re-deriven en el próximo snapshot.
- Tests: los use cases se prueban con puertos en memoria y `StepVerifier`. La integración (adaptador R2DBC, inbox y REST) corre contra **Postgres local real** (`agent_project_test`, ver `src/test/resources/application-test.yml`): no hay Docker en la máquina de desarrollo y el esquema usa JSONB, que H2 no soporta.
- Sin comentarios que expliquen QUÉ hace el código. Comentarios solo para el PORQUÉ no obvio.
- No añadir abstracciones, validaciones o manejo de errores para escenarios que no pueden ocurrir. YAGNI.
- Frontend: componentes standalone, lazy-loaded por feature, signals para estado local, sin `NgModule`.

## Proceso de trabajo

- Antes de escribir código nuevo, leer `docs/PROGRESS.md` (próximo paso recomendado) y `docs/ROADMAP.md` (etapa actual) para no duplicar ni desordenar el orden de dependencias.
- Al cerrar una tarea: actualizar `docs/PROGRESS.md` y marcar el checkbox correspondiente en `docs/ROADMAP.md` **solo si fue verificado** (build/test/llamada real), no por haber sido escrito.
- Decisiones de producto o arquitectura que impliquen descartar una alternativa razonable: preguntar al usuario si no hay una instrucción explícita previa, y registrar la decisión (con el motivo dado) en `docs/DECISIONS.md`.
- No inventar alcance no pedido.
