# Spec Delta

## Purpose

Estima cuánto trabajo con Claude cabe hoy, a partir del uso local registrado y del % disponible que indica Daniel, para dimensionar el daily.

## ADDED Requirements

### Requirement: Medición del uso local
El generador SHALL reportar en `usage` el uso de los últimos 28 días por día (America/Bogota): el total y por repo del portafolio. La métrica es `input + cache_creation + output`; las lecturas de caché no cuentan. Cada respuesta SHALL contarse una sola vez (deduplicada por id de mensaje). El repo SHALL salir del `cwd` de la sesión: el primer directorio bajo la raíz del workspace, y si no coincide con ningún repo, "otros". El generador MUST NOT enviar texto de las conversaciones.

#### Scenario: Respuesta repetida en el transcript
- **WHEN** la misma respuesta aparece varias veces en un `.jsonl`
- **THEN** sus tokens se suman una sola vez

#### Scenario: Sesión en un subdirectorio
- **WHEN** el `cwd` de una sesión es `…/srdejo/beauty/backend`
- **THEN** su uso cuenta para `beauty`

### Requirement: Costo por tarea
El backend SHALL calcular el costo por tarea como los tokens de los últimos 28 días divididos por las tareas completadas en esos días (incrementos de `done` en el historial de avance de todos los proyectos). Si hay menos de 5 tareas completadas en el período, el costo SHALL ser `null` y el daily no se recorta.

#### Scenario: Historial insuficiente
- **WHEN** en 28 días se completaron 3 tareas
- **THEN** `budget.costPerTask` es `null` y el daily muestra el foco y las dos alternativas, como sin presupuesto

### Requirement: Presupuesto del día
Con `availablePercent` entre 0 y 100, el sistema SHALL estimar:
- el **cupo semanal**: los tokens de los últimos 7 días divididos por la fracción usada (`1 − availablePercent/100`);
- lo **disponible**: cupo × `availablePercent/100`;
- el **presupuesto de hoy**: lo disponible repartido entre los días que quedan hasta el próximo lunes, contando hoy;
- las **tareas que caben**: el presupuesto de hoy dividido por el costo por tarea, redondeado hacia abajo.

Si la fracción usada es 0, o no hay uso en 7 días, el presupuesto SHALL ser `null`, con el motivo. La respuesta y el texto del daily SHALL decir que todo es una estimación.

#### Scenario: Cupo casi agotado
- **WHEN** `availablePercent` es 10 y caben 2 tareas hoy
- **THEN** el daily mantiene el foco, quita las alternativas e indica "Presupuesto estimado: ~2 tareas"

#### Scenario: Margen amplio
- **WHEN** caben 12 tareas hoy
- **THEN** el daily muestra el foco y las dos alternativas, con el presupuesto estimado

### Requirement: Recorte del daily
Si caben menos tareas que la siguiente tarea del foco más una por alternativa, SHALL quitar alternativas desde la última. El foco SHALL mantenerse siempre, aunque caben 0, y entonces el daily advierte que el presupuesto no alcanza. Sin `availablePercent`, el daily SHALL ser el mismo que antes de este cambio.

#### Scenario: Sin porcentaje
- **WHEN** se pide `GET /api/daily` sin `availablePercent`
- **THEN** la respuesta es igual a la de `daily-plan` y `budget` es `null`

### Requirement: Entrada del porcentaje en el dashboard
El bloque "Hoy" SHALL ofrecer un campo "% disponible según /usage". El valor se recuerda en el navegador y se envía como `availablePercent`. SHALL mostrar el presupuesto estimado, el costo por tarea y el uso de los últimos 7 días. Un valor fuera de 0–100 SHALL rechazarse con un mensaje, sin llamar al backend.

#### Scenario: Recargar la página
- **WHEN** Daniel escribió 40 ayer y hoy abre el dashboard
- **THEN** el campo trae 40 y el daily llega dimensionado con ese valor
