# Spec Delta

## Purpose

Registra bloqueos y decisiones pendientes por proyecto con un tipo explícito, para que no se confundan decisiones de producto, dependencias externas y fallos técnicos, y para que una misma decisión no se repita como varios problemas distintos. Reemplaza el antiguo campo `open_question`.

## ADDED Requirements

### Requirement: Bloqueos tipados en la capa editorial
Cada entrada del snapshot SHALL aceptar `blockers`: una lista de objetos con estos campos:
- `id` (estable dentro del proyecto);
- `type`, uno de `PRODUCT_DECISION | EXTERNAL_DEPENDENCY | TECHNICAL | ENVIRONMENT | SECURITY | UNKNOWN`;
- `description` (requerido);
- campos opcionales: `impact`, `affects` (referencias de texto a tareas, etapas o `openspec:<cambio>`), `owner`, `created` (fecha ISO) y `resolution` (condición para resolverlo).

La lista SHALL reemplazar por completo los bloqueos guardados del proyecto.

#### Scenario: Bloqueo válido
- **WHEN** llega `{id: "pagos", type: "PRODUCT_DECISION", description: "Elegir proveedor de pagos", affects: ["Fase 3", "Fase 4"]}`
- **THEN** se guarda y aparece en el detalle del proyecto con su tipo y sus 2 elementos afectados

#### Scenario: Tipo desconocido
- **WHEN** un bloqueo trae `type: "LEGAL"`
- **THEN** se descarta solo ese bloqueo y queda un aviso en el sync run; el resto de la entrada se aplica

#### Scenario: Bloqueo resuelto
- **WHEN** un snapshot deja de incluir el bloqueo `pagos`
- **THEN** el bloqueo deja de mostrarse

### Requirement: Una decisión, un registro
Una decisión que afecta a varios elementos SHALL representarse como un solo bloqueo con varios `affects`. El dashboard SHALL mostrarla una sola vez, con el número de elementos afectados.

#### Scenario: Subscription Hub
- **WHEN** el proyecto tiene un único `PRODUCT_DECISION` "proveedor de pagos" con 4 `affects`
- **THEN** la decisión aparece una sola vez, con "4 elementos afectados"

### Requirement: Elementos afectados resueltos
El backend SHALL intentar resolver cada referencia de `affects` contra las etapas y los cambios del proyecto, con la misma regla que el alcance del objetivo (spec `progress-model`). SHALL exponer cuántas tareas abiertas quedan bajo los elementos resueltos. Una referencia que no se resuelve SHALL mostrarse como texto, sin contar tareas.

#### Scenario: Afecta a una etapa
- **WHEN** un bloqueo afecta a `Fase 3`, que tiene 6 tareas sin hacer
- **THEN** el bloqueo indica "6 tareas abiertas afectadas"
