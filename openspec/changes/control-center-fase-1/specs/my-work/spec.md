# Spec Delta

## Purpose

Es la página de inicio privada, "Mi trabajo". En una sola pantalla responde qué se puede avanzar ahora, qué cambio quedó a medias, qué decisión desbloquea más trabajo, qué necesita revisión y si los datos están al día. Usa solo datos que existen.

## ADDED Requirements

### Requirement: Endpoint agregado de trabajo
El sistema SHALL exponer `GET /api/work`, que devuelve las secciones `continue`, `actionable`, `decisions`, `review` y `activity`, calculadas sobre todos los proyectos cuyo estado no sea `PAUSED`, `COMPLETED` ni `ARCHIVED`.

#### Scenario: Respuesta completa
- **WHEN** se llama `GET /api/work`
- **THEN** la respuesta contiene las cinco secciones, aunque alguna esté vacía

#### Scenario: Proyecto pausado
- **WHEN** un proyecto tiene `state = PAUSED`
- **THEN** no aporta elementos a ninguna sección

### Requirement: Sección Continuar
`continue` SHALL listar los cambios OpenSpec activos con trabajo parcial, es decir, con al menos una tarea `DONE` y al menos una `OPEN`. Cada elemento incluye el proyecto, el cambio, los contadores `DONE` y total, la primera tarea pendiente (número y texto) y si el cambio está dentro del objetivo actual. El orden es: primero los cambios dentro del objetivo, luego por `priorityRank` (los proyectos sin rango al final) y después por nombre.

#### Scenario: Cambio a medias en el objetivo
- **WHEN** `nolost/kids-registro-y-asistencia` tiene 20 de 31 tareas hechas y está en el alcance del objetivo
- **THEN** aparece primero en `continue`, con `20/31` y su primera tarea pendiente como siguiente paso

### Requirement: Sección Accionables ahora
`actionable` SHALL listar las tareas `OPEN` o `IN_PROGRESS`, tanto del ROADMAP como de los cambios activos, excepto las que estén bajo algún elemento de `affects` de un bloqueo vigente, una vez resuelta la referencia (spec `project-blockers`). El orden es:
1. proyecto por `priorityRank`;
2. dentro del proyecto, primero las tareas del alcance del objetivo;
3. luego `IN_PROGRESS` antes que `OPEN`;
4. finalmente, el orden original del archivo.

Cada elemento SHALL indicar su origen (etapa del ROADMAP o cambio y número). La sección SHALL limitarse a 5 elementos por proyecto, con el conteo de los restantes.

#### Scenario: Tarea afectada por una decisión
- **WHEN** `Fase 3` está en `affects` de un bloqueo `PRODUCT_DECISION`
- **THEN** sus tareas no aparecen en `actionable`

#### Scenario: Tarea bloqueada por falta de definición
- **WHEN** una tarea está `BLOCKED`
- **THEN** no aparece en `actionable`

### Requirement: Sección Decisiones que desbloquean
`decisions` SHALL listar los bloqueos `PRODUCT_DECISION` de todos los proyectos incluidos, con el proyecto, la descripción, el responsable y las tareas abiertas afectadas. El orden es por tareas abiertas afectadas, de mayor a menor, y después por `priorityRank`. Si una decisión no tiene `affects` resolubles, SHALL mostrar "afectados: sin datos" y no un número inferido.

#### Scenario: Orden por impacto
- **WHEN** una decisión afecta a 6 tareas abiertas y otra a 1
- **THEN** la de 6 aparece primero

### Requirement: Sección Revisión requerida
`review` SHALL listar:
- los cambios activos con todas sus tareas `DONE` o `CANCELLED` ("listo para verificar y archivar");
- los objetivos cumplidos sin un objetivo siguiente declarado;
- los proyectos sin objetivo declarado;
- los proyectos con error de lectura o ausentes;
- los cambios con error de parseo.

Esta sección MUST NOT simular ejecuciones de agente pendientes de revisión.

#### Scenario: Cambio completo sin archivar
- **WHEN** todas las tareas de `visionarios/add-remember-password-login` están `DONE` y el cambio sigue activo
- **THEN** aparece en `review` como listo para verificar y archivar

#### Scenario: Proyecto sin objetivo
- **WHEN** el ROADMAP de un proyecto no declara `## Objetivo actual`
- **THEN** aparece en `review` como "declara un objetivo"

### Requirement: Sección Actividad reciente
`activity` SHALL listar los últimos 20 eventos ordenados por fecha. Los eventos son de dos tipos:
- los sync runs, con creados, actualizados, rechazados y errores;
- los cambios de avance del objetivo entre dos puntos consecutivos del historial.

#### Scenario: Avance registrado
- **WHEN** el último sync subió el objetivo de `distriapp` de 21/36 a 24/36
- **THEN** `activity` incluye "distriapp · MVP E2E sin mocks: 21/36 → 24/36"

### Requirement: Vista Mi trabajo como inicio
El frontend SHALL mostrar "Mi trabajo" en `/`, con las cinco secciones y la cabecera de salud (spec `sync-health`). Cada elemento SHALL enlazar a su proyecto o a su cambio. Una sección vacía SHALL mostrar un estado vacío explícito. Los textos de tarea largos se recortan a 2 líneas y se pueden expandir.

#### Scenario: Navegar a un cambio
- **WHEN** el usuario hace clic en un elemento de "Continuar"
- **THEN** se abre `/projects/:id/changes/:name`

#### Scenario: Sin decisiones
- **WHEN** no hay ningún `PRODUCT_DECISION`
- **THEN** la sección muestra "Sin decisiones pendientes"
