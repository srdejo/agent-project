# daily-plan Specification

## Purpose
Le dice a Daniel, al empezar el día, en qué trabajar, qué necesita resolver, qué comandos correr y cómo retomar. Todo sale de los datos sincronizados y con reglas fijas.

## Requirements

### Requirement: Endpoint del daily
El sistema SHALL exponer `GET /api/daily`. Devuelve:
- la fecha (zona America/Bogota);
- `focus` y hasta dos `alternatives`;
- `needs`;
- `commands`;
- `continuation`;
- `stale`;
- `text`: el daily completo en texto plano.

No tiene efectos. Con los mismos datos y la misma fecha SHALL devolver el mismo resultado.

#### Scenario: Llamadas repetidas el mismo día
- **WHEN** se pide el daily dos veces sin un sync de por medio
- **THEN** ambas respuestas son idénticas

### Requirement: Elección del foco y las alternativas
Los candidatos SHALL salir, en este orden:
1. los elementos de "Continuar" de Mi trabajo, es decir, cambios OpenSpec a medias, en su mismo orden (primero los del objetivo, luego por `priorityRank`);
2. después, para cada proyecto que todavía no tenga candidato, su primer elemento de "Accionables ahora".

El foco SHALL ser el primer candidato. Las alternativas SHALL ser los dos siguientes candidatos **de proyectos distintos al foco y entre sí**. Si no hay candidatos, `focus` es `null` y el texto lo dice.

#### Scenario: Cambio a medias en el objetivo
- **WHEN** `nolost/kids-registro-y-asistencia` está en "Continuar", dentro de su objetivo, y es el primero
- **THEN** es el foco, con su siguiente tarea y su avance `done/total`

#### Scenario: Alternativas de otros proyectos
- **WHEN** los tres primeros candidatos son dos cambios de `nolost` y uno de `distriapp`
- **THEN** el foco es el primero de `nolost` y las alternativas son el de `distriapp` y el siguiente candidato de un tercer proyecto

### Requirement: Para trabajar necesito
`needs` SHALL listar los bloqueos de los proyectos del foco y de las alternativas, empezando por el foco, con proyecto, tipo, descripción y responsable si lo hay. Si esos proyectos no tienen bloqueos, SHALL listar hasta dos decisiones de "Decisiones que desbloquean" del portafolio. Si tampoco hay, la sección dice "Nada pendiente de terceros".

#### Scenario: El foco tiene una decisión pendiente
- **WHEN** el proyecto del foco tiene un bloqueo `PRODUCT_DECISION`
- **THEN** ese bloqueo encabeza `needs`

### Requirement: Comandos para la consola
`commands` SHALL ser los comandos para empezar con el foco, desde la raíz del workspace:
- `cd <repo>`;
- `git status`;
- `git pull`;
- si el foco es un cambio OpenSpec y el repo tiene instalado `apply`: `claude` y luego `/opsx:apply <cambio>`;
- si no: `claude`, y pegar la instrucción de "Para continuar".

El dashboard los muestra para copiarlos y MUST NOT ejecutarlos.

#### Scenario: Repo sin opsx apply
- **WHEN** el repo del foco no tiene `apply` en sus comandos
- **THEN** los comandos no incluyen `/opsx:apply` y piden pegar la instrucción de continuación

### Requirement: Para continuar
`continuation` SHALL ser:
- si el foco es un cambio OpenSpec, la misma instrucción que `GET /api/projects/{id}/changes/{name}/continuation`;
- si el foco es una tarea del ROADMAP, una instrucción con el proyecto, la etapa, la tarea, el objetivo si la tarea está en él, y el recordatorio de revisar `git status` y de marcar la tarea solo cuando esté verificada.

#### Scenario: Foco del ROADMAP
- **WHEN** el foco es una tarea del ROADMAP de un proyecto sin OpenSpec
- **THEN** la instrucción nombra la etapa y la tarea, e incluye el criterio de cierre del objetivo si la tarea forma parte de él

### Requirement: Bloque Hoy en Mi trabajo
El frontend SHALL mostrar el daily como primer bloque de `/`, con las cuatro secciones, enlaces al proyecto o cambio de cada elemento y un botón **Copiar daily** que copia `text`. Si `stale` es `true`, SHALL mostrar el aviso de datos obsoletos dentro del bloque. MUST NOT haber ningún botón para ejecutar.

#### Scenario: Copiar el daily
- **WHEN** Daniel pulsa "Copiar daily"
- **THEN** el portapapeles contiene el texto completo y el botón confirma la copia
