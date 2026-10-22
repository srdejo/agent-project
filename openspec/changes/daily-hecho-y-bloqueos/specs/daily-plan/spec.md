# Spec Delta

## ADDED Requirements

### Requirement: Hecho
El daily SHALL incluir `done`: la actividad del período que empieza a las 00:00 (America/Bogota) del último día anterior
a hoy con actividad, dentro de los últimos 7 días, y llega hasta ahora. Ese inicio SHALL viajar en `doneSince`. Si no
hubo actividad en esos 7 días, el período es solo hoy.

`done` SHALL agruparse por proyecto, ordenado por prioridad. Cada grupo trae sus tareas cerradas (número y texto, con el
cambio o la etapa) y sus commits (`sha` y asunto), los más recientes primero, con un máximo de 8 commits por proyecto y
el número de los que no se muestran. Los proyectos sin actividad en el período no aparecen. Si nadie tuvo actividad, la
sección dice "Sin actividad registrada".

#### Scenario: Lunes después de trabajar el viernes
- **WHEN** hoy es lunes, hubo commits el viernes y no hubo nada el sábado ni el domingo
- **THEN** `doneSince` es el viernes a las 00:00 y `done` incluye los commits del viernes y los de hoy

#### Scenario: Tarea cerrada y commits del mismo proyecto
- **WHEN** ayer se cerró `distriapp` 4.8 y hubo 10 commits en `distriapp`
- **THEN** el grupo de `distriapp` muestra la tarea 4.8, 8 commits y "2 más"

### Requirement: Bloqueos
El daily SHALL incluir `blockers`, en este orden:
1. los bloqueos editoriales de todos los proyectos `ACTIVE`, `NEXT` y `BLOCKED`, por prioridad, con proyecto, tipo,
   descripción y responsable si lo hay;
2. las tareas bloqueadas por el marcador de texto en esos mismos proyectos, con proyecto, cambio o etapa, número, texto
   corto y motivo.

Las tareas bloqueadas solo por "falta de definición" de su etapa MUST NOT listarse. Si no hay nada, la sección dice
"Nada bloqueado".

#### Scenario: Bloqueo de un proyecto que no es el foco
- **WHEN** el foco es `distriapp` y `subscription-hub` tiene el bloqueo "Elegir proveedor de pagos"
- **THEN** ese bloqueo aparece en `blockers`

#### Scenario: Tarea bloqueada por texto
- **WHEN** la tarea 4.9 de `distriapp` está bloqueada por el marcador
- **THEN** aparece en `blockers` con su motivo y no es el foco ni una alternativa

## MODIFIED Requirements

### Requirement: Endpoint del daily
El sistema SHALL exponer `GET /api/daily`. Devuelve:
- la fecha (zona America/Bogota);
- `doneSince` y `done`;
- `focus` y hasta dos `alternatives`;
- `blockers`;
- `commands`;
- `continuation`;
- `stale`;
- `text`: el daily completo en texto plano, con las secciones Hecho, Para hoy trabajaré en y Bloqueos, seguidas de los
  comandos y la continuación.

No tiene efectos. Con los mismos datos y la misma fecha SHALL devolver el mismo resultado.

#### Scenario: Llamadas repetidas el mismo día
- **WHEN** se pide el daily dos veces sin un sync de por medio
- **THEN** ambas respuestas son idénticas

#### Scenario: Texto copiable
- **WHEN** se pide el daily
- **THEN** `text` contiene, en orden, las secciones "Hecho", "Para hoy trabajaré en" y "Bloqueos"

### Requirement: Bloque Hoy en Mi trabajo
El frontend SHALL mostrar el daily como primer bloque de `/`, con las secciones Hecho, Para hoy trabajaré en y
Bloqueos, más los comandos y la continuación. Cada elemento enlaza a su proyecto o cambio, y hay un botón **Copiar
daily** que copia `text`. Si `stale` es `true`, SHALL mostrar el aviso de datos obsoletos dentro del bloque. MUST NOT
haber ningún botón para ejecutar.

#### Scenario: Copiar el daily
- **WHEN** Daniel pulsa "Copiar daily"
- **THEN** el portapapeles contiene el texto completo y el botón confirma la copia

#### Scenario: Las tres preguntas a la vista
- **WHEN** Daniel abre `/` en un celular de 375 px
- **THEN** ve las secciones Hecho, Para hoy trabajaré en y Bloqueos sin scroll horizontal

## REMOVED Requirements

### Requirement: Para trabajar necesito
**Reason**: Solo mostraba los bloqueos de los tres proyectos elegidos y ocultaba los demás.
**Migration**: Usar `blockers` (requisito "Bloqueos"), que cubre todos los proyectos activos, próximos y bloqueados.
