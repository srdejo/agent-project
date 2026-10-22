# Spec Delta

## Purpose

Da al backend la actividad reciente de cada repo, es decir sus commits y las tareas que se cerraron entre dos syncs, para
que el daily pueda decir qué se hizo sin que nadie lo escriba a mano.

## ADDED Requirements

### Requirement: Commits recientes en el snapshot
El snapshot SHALL poder traer un bloque opcional de primer nivel `activity` con `since` (ISO-8601 con offset) y
`projects`, un objeto `id -> commits[]`. Cada commit es `{sha, at, subject}`, con `at` en ISO-8601 con offset. El
generador SHALL enviar los commits sin merges de todas las ramas de los últimos 7 días, sin duplicados por `sha` y como
máximo 30 por proyecto, los más recientes primero. `format_version` SHALL seguir siendo `2`. Un snapshot sin `activity`
SHALL aplicarse igual que hoy.

#### Scenario: Snapshot con actividad
- **WHEN** llega un snapshot con `activity.projects.distriapp` con tres commits
- **THEN** el backend guarda esos tres commits para `distriapp` y el resultado de los proyectos no cambia

#### Scenario: Snapshot sin actividad
- **WHEN** llega un snapshot v2 sin el bloque `activity`
- **THEN** se aplica sin errores y los commits guardados antes no se borran

### Requirement: La actividad no altera la detección de cambios
Los commits de `activity` MUST NOT formar parte del hash de contenido de la entrada del proyecto. Si la entrada de un
proyecto es idéntica a la anterior y solo cambia su lista en `activity`, el proyecto SHALL quedar `UNCHANGED`.

#### Scenario: Solo cambia la actividad
- **WHEN** entre dos syncs la entrada de `nolost` es idéntica y solo cambia `activity.projects.nolost`
- **THEN** `nolost` figura como `UNCHANGED` en el sync run y sus commits guardados se actualizan

### Requirement: Reemplazo de los commits por proyecto
Para cada proyecto presente en `activity.projects`, el backend SHALL reemplazar sus commits guardados por los recibidos.
Los proyectos ausentes de `activity.projects` conservan los suyos. Un commit con `sha`, `at` o `subject` faltante o
inválido SHALL descartarse con un aviso en el sync run.

#### Scenario: Commit inválido
- **WHEN** un commit llega sin `sha`
- **THEN** se descarta, el sync run registra un aviso para ese proyecto y el resto se guarda

### Requirement: Tareas cerradas entre syncs
Cuando el backend aplica una entrada `UPDATED`, SHALL registrar como cerrada cada tarea que antes estaba sin marcar y
ahora está `[x]` y no tachada, con su proyecto, su origen (ROADMAP con la etapa, u OpenSpec con el cambio), su número si
lo tiene, su texto y como fecha el `generated_at` del snapshot. La tarea se identifica por origen, etapa o cambio, y
número, o el texto si no tiene número. Una tarea nueva que ya llega marcada MUST NOT registrarse, y tampoco las tareas
de un proyecto que aparece por primera vez (`CREATED`).

#### Scenario: Se marca una tarea de OpenSpec
- **WHEN** `distriapp/mvp-e2e-sin-mocks` 4.8 pasa de `[ ]` a `[x]` entre dos syncs
- **THEN** queda registrada como cerrada con la fecha del segundo snapshot

#### Scenario: Cambio nuevo ya avanzado
- **WHEN** aparece un cambio OpenSpec nuevo con tareas ya marcadas
- **THEN** esas tareas no se registran como cerradas
