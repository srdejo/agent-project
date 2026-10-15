# Spec Delta

## Purpose

Permite abrir un cambio OpenSpec desde su proyecto, ver sus artefactos reales y preparar una instrucción de continuación lista para copiar al agente. No ejecuta nada ni escribe en ningún repositorio.

## ADDED Requirements

### Requirement: Listado de cambios de un proyecto
El sistema SHALL exponer `GET /api/projects/{id}/changes`. Por cada cambio activo devuelve: el nombre, los artefactos presentes, el número de tareas `DONE` sobre el total no cancelado, si forma parte del objetivo actual, y el error de parseo con su fecha (si lo hay). Además devuelve `archivedCount`, `specsCount`, `commands` y `openspecDetected`. Un proyecto inexistente SHALL responder 404.

#### Scenario: Proyecto sin OpenSpec
- **WHEN** el proyecto existe pero su repo no tiene `openspec/`
- **THEN** la respuesta es 200 con `openspecDetected = false` y `changes` vacío

### Requirement: Detalle de un cambio con artefactos
El sistema SHALL exponer `GET /api/projects/{id}/changes/{name}`. Devuelve el contenido de cada artefacto presente, con su indicador de truncado, la `metadata` y las tareas con su número, sección, línea y estado. Un cambio inexistente SHALL responder 404.

#### Scenario: Cambio sin design
- **WHEN** se abre un cambio que no tiene `design.md`
- **THEN** la vista muestra solo los artefactos presentes e indica "design.md no existe en este cambio"

### Requirement: Renderizado seguro
El frontend SHALL mostrar los artefactos como texto literal. Ningún contenido de artefacto SHALL inyectarse como HTML.

#### Scenario: HTML embebido
- **WHEN** un `proposal.md` contiene `<script>alert(1)</script>`
- **THEN** se muestra como texto y no se ejecuta

### Requirement: Instrucción de continuación
El sistema SHALL exponer `GET /api/projects/{id}/changes/{name}/continuation`, que devuelve un texto generado solo a partir de los datos sincronizados. El texto incluye:
- el proyecto, el repo y el cambio;
- el objetivo actual y su criterio de cierre, si el cambio forma parte de él;
- las rutas relativas de los artefactos presentes;
- las tareas pendientes en orden, con número y texto, y un resumen de las hechas;
- la primera tarea pendiente como siguiente paso;
- la fecha de generación del snapshot y su antigüedad;
- la indicación de revisar primero el estado real del repo (`git status` y los artefactos actuales);
- el comando sugerido, solo si está en `commands`: `/opsx:apply <name>` si quedan tareas pendientes, o `/opsx:archive <name>` si no queda ninguna.

La generación SHALL ser determinista y MUST NOT tener efectos: no crea registros ni envía nada.

#### Scenario: Comando no instalado
- **WHEN** el repo no tiene `apply` en `commands`
- **THEN** la instrucción no menciona `/opsx:apply` y describe el siguiente paso en lenguaje natural

#### Scenario: Datos obsoletos
- **WHEN** `stale = true` (spec `sync-health`)
- **THEN** la instrucción empieza con una advertencia de que el estado puede estar desactualizado

#### Scenario: Llamadas repetidas
- **WHEN** se pide la continuación dos veces sin un sync de por medio
- **THEN** ambas respuestas son idénticas y no se crea ningún registro

### Requirement: Vista previa editable y copia
La vista del cambio (`/projects/:id/changes/:name`) SHALL mostrar la instrucción en un área de texto editable con un botón "Copiar", que copia el texto editado. La vista MUST NOT ofrecer botones para ejecutar ni para enviar al agente en esta entrega. Si el agente está `offline`, la vista SHALL indicarlo sin impedir que se copie la instrucción.

#### Scenario: Copiar con edición
- **WHEN** el usuario edita la instrucción y pulsa "Copiar"
- **THEN** el portapapeles contiene el texto editado y la vista confirma la copia
