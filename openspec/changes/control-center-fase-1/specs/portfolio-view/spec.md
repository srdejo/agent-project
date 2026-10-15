# Spec Delta

## Purpose

Define la API y las vistas del portafolio y del detalle de un proyecto: estado declarado del proyecto, avance hacia su objetivo, bloqueos, filtros y orden. Reemplaza el listado, el mapa y el detalle anteriores.

## ADDED Requirements

### Requirement: Estado de proyecto declarado
Cada proyecto SHALL tener un `state` declarado en la capa editorial, que es uno de `ACTIVE | NEXT | PAUSED | BLOCKED | MAINTENANCE | COMPLETED | ARCHIVED`. El estado MUST NOT derivarse del porcentaje.

#### Scenario: Proyecto terminado con trabajo nuevo
- **WHEN** un proyecto tiene `state = MAINTENANCE` y su objetivo actual va en 40%
- **THEN** se muestra como `MAINTENANCE` con 40% de avance, sin contradicción

### Requirement: API del portafolio
El sistema SHALL exponer `GET /api/portfolio`, que devuelve una tarjeta por proyecto con:
- identificación: id, nombre, alias, resumen, `state`, `priorityRank` y stack;
- objetivo: nombre, DONE, denominador, porcentaje (`null` si no hay objetivo o el denominador es 0) y la marca de cumplido;
- roadmap: DONE y denominador;
- OpenSpec: cambios activos;
- bloqueos: número de bloqueos y el bloqueo principal (el de mayor número de tareas afectadas);
- actividad: `lastActivity` (`source_modified_at`), `missingSince` y el error de lectura, si lo hay;
- `hasActionable`: si el proyecto aporta elementos a `actionable` de `my-work`.

Los proyectos `ARCHIVED` SHALL incluirse solo con `?includeArchived=true`.

#### Scenario: Listado por defecto
- **WHEN** se llama `GET /api/portfolio`
- **THEN** aparecen todos los proyectos salvo los `ARCHIVED`, cada uno con los bloques de objetivo y roadmap

### Requirement: API del detalle de proyecto
El sistema SHALL exponer `GET /api/projects/{id}`, que devuelve:
- los datos de la tarjeta;
- el objetivo actual: criterio, fecha objetivo, referencias de alcance con su estado de resolución y las tareas del alcance agrupadas por referencia;
- los objetivos cumplidos;
- las etapas del ROADMAP con sus tareas y estados (incluido el motivo de `BLOCKED`);
- los bloqueos con sus elementos afectados resueltos;
- el historial del objetivo actual;
- la indicación "evidencia independiente: no disponible".

Si el id no existe, SHALL responder 404.

#### Scenario: Id inexistente
- **WHEN** se pide `GET /api/projects/no-existe`
- **THEN** la respuesta es 404

### Requirement: Vista del portafolio
El frontend SHALL mostrar el portafolio en `/portfolio`. Cada tarjeta muestra:
- nombre, resumen, estado, rango y nombre del objetivo;
- el avance como `NN% (done/total)`, o bien "Sin objetivo declarado" o "Sin datos";
- el avance secundario del roadmap;
- el número de cambios activos y el bloqueo principal;
- la acción principal: "Continuar <cambio>", "Declarar objetivo" o "Ver decisión", según el caso.

#### Scenario: Sin objetivo
- **WHEN** un proyecto no tiene objetivo
- **THEN** su tarjeta muestra "Sin objetivo declarado" en lugar de una barra, y la acción "Declarar objetivo"

### Requirement: Filtros y orden del portafolio
La vista del portafolio SHALL permitir:
- **filtrar** por estado, presencia de bloqueos, stack, "con trabajo ejecutable ahora" (`hasActionable`) y "sin objetivo";
- **ordenar** por `priorityRank` (por defecto, con los proyectos sin rango al final), avance del objetivo, número de bloqueos y última actividad.

Los filtros SHALL combinarse y reflejarse en la URL.

#### Scenario: Filtro combinado
- **WHEN** el usuario filtra `state = ACTIVE` y "con bloqueos"
- **THEN** solo aparecen los proyectos `ACTIVE` con al menos un bloqueo, y al recargar la URL se conserva el filtro

### Requirement: Vista de detalle de proyecto
El frontend SHALL mostrar el detalle en `/projects/:id`, organizado en estas pestañas:
- **Resumen**: estado, objetivo con avance, gráfica del objetivo y acciones siguientes;
- **Objetivo y tareas**: el alcance con sus referencias y las tareas agrupadas, con estado y archivo de origen; los ROADMAP fuera del objetivo aparecen plegados;
- **Cambios OpenSpec**;
- **Bloqueos y decisiones**;
- **Actividad**.

#### Scenario: Referencia no resuelta visible
- **WHEN** el objetivo tiene `Etapa 9` sin resolver
- **THEN** la pestaña "Objetivo y tareas" muestra el aviso junto al alcance
