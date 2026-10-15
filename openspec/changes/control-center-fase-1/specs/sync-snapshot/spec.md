# Spec Delta

## Purpose

Define cómo llega al dashboard el estado de cada repositorio del workspace: un snapshot completo y versionado que genera la máquina del desarrollador y se deposita en el inbox del servidor. Incluye el ROADMAP, el objetivo, OpenSpec y la capa editorial. Ningún repositorio se modifica en el proceso.

## ADDED Requirements

### Requirement: Un snapshot versionado por corrida
El generador SHALL producir un único archivo `snapshot.json` con:
- `format_version`: `2`;
- `generator_version`;
- `generated_at` (ISO-8601 con offset);
- `projects`: un mapa `id -> entrada`.

El archivo SHALL depositarse en el inbox de forma atómica, con escritura `.tmp` y renombrado. El backend SHALL procesarlo al detectarlo y borrarlo al terminar. Los archivos `progreso.json` y `nuevo.json` dejan de procesarse: si aparecen, el run registra un error de archivo "formato obsoleto" y los borra.

#### Scenario: Snapshot válido
- **WHEN** aparece `snapshot.json` con `format_version: 2` y 11 proyectos válidos
- **THEN** se aplican los 11 proyectos, el run queda registrado y el archivo se borra

#### Scenario: Versión desconocida
- **WHEN** llega `snapshot.json` con `format_version: 3`
- **THEN** no se aplica ningún proyecto, el run registra el error de archivo y los datos guardados quedan intactos

#### Scenario: Formato antiguo
- **WHEN** aparece `progreso.json` en el inbox
- **THEN** no se aplica, el run registra "formato obsoleto: progreso.json" y el archivo se borra

### Requirement: Contenido de la entrada de un proyecto
Cada entrada SHALL incluir:
- **capa editorial**: `name`, `repo`, `summary`, `alias`, `state`, `priority_rank`, `stack` y `blockers`;
- **origen**: `source_commit` y `source_modified_at`;
- **`roadmap`**, presente si existe `docs/ROADMAP.md`, con:
  - `stages`: cada etapa con su clave (`Etapa 5`, `Fase 2`), título, marcador declarado (`closed`, `in_progress` o `none`), si tiene criterio de cierre, si es backlog, y sus tareas (texto, línea, `checked` y `cancelled`);
  - `objective`, si existe la sección `## Objetivo actual`, con su nombre, criterio, alcance (lista de referencias en texto) y fecha objetivo opcional;
  - `achieved`: los objetivos cumplidos;
- **`openspec`**, presente si existe `openspec/`, con:
  - `specs_count`, `archived_count` y `commands`;
  - `changes`: los cambios activos, cada uno con sus artefactos, `metadata` y tareas;
  - `archived_in_scope`: los cambios archivados referenciados por el alcance del objetivo, solo con sus tareas;
- **`error`**: si el repo no se pudo leer, la entrada lleva solo `error`.

El generador MUST NOT calcular porcentajes ni estados de tarea: solo reporta hechos del archivo.

#### Scenario: Repo con ROADMAP y OpenSpec
- **WHEN** el generador lee un repo con `docs/ROADMAP.md` y `openspec/changes/x/`
- **THEN** la entrada trae `roadmap` y `openspec`, y ninguna tarea trae porcentaje ni estado derivado

#### Scenario: Repo solo con OpenSpec
- **WHEN** un repo listado en la capa editorial no tiene `docs/ROADMAP.md`, pero sí `openspec/`
- **THEN** la entrada se envía sin `roadmap` y con `openspec`

### Requirement: Tareas de tasks.md conservadas tal cual
Por cada `tasks.md`, el generador SHALL reportar cada línea de checkbox con:
- su número tal como aparece (`3.4`, o vacío si no tiene);
- su texto íntegro, su orden, su sección `##` y su línea;
- `checked`;
- `cancelled`, cuando el texto está tachado (`~~…~~`).

Las subtareas anidadas SHALL conservarse en orden como tareas propias.

#### Scenario: Tarea numerada marcada
- **WHEN** `tasks.md` contiene `- [x] 1.1 Agregar ...` bajo `## 1. Consultas internas`
- **THEN** se reporta con número `1.1`, sección `1. Consultas internas`, texto íntegro y `checked = true`

### Requirement: Artefactos OpenSpec con límite de tamaño
Por cada cambio activo, el generador SHALL enviar el contenido de cada artefacto que existe (`proposal.md`, `design.md`, `tasks.md`, cada `specs/**/spec.md` con su ruta relativa y `.openspec.yaml` como `metadata`), sin asumir que existen todos. Cada artefacto se limita a 200 KB; si supera el límite, se envía truncado y marcado `truncated`. Si un cambio no se puede leer, SHALL enviarse como `{name, error}`.

#### Scenario: Cambio sin design
- **WHEN** un cambio tiene `proposal.md` y `tasks.md`, pero no `design.md`
- **THEN** se envía sin `design` y no se considera error

### Requirement: Comandos opsx instalados
El generador SHALL reportar en `commands` los comandos `opsx` instalados en el repo, según los archivos de `.claude/commands/opsx/`.

#### Scenario: Comando ausente
- **WHEN** el repo no tiene `verify.md` en `.claude/commands/opsx/`
- **THEN** `commands` no incluye `verify`

### Requirement: Reemplazo completo por proyecto con detección de cambios
Por cada entrada válida, el backend SHALL calcular un hash del contenido normalizado. Si coincide con el guardado, no modifica nada (`UNCHANGED`). Si es distinto, reemplaza por completo el estado del proyecto (tareas, etapas, objetivo, cambios y bloqueos) y registra `UPDATED`. Si el id no existía, lo crea (`CREATED`).

#### Scenario: Cambio sin commit en tasks.md
- **WHEN** se marca un checkbox en `tasks.md` sin hacer commit y se corre el sync
- **THEN** el hash cambia y el proyecto se actualiza, sin depender de fechas de git

#### Scenario: Mismo contenido
- **WHEN** dos snapshots consecutivos traen la misma entrada para `hotel`
- **THEN** la segunda corrida registra `hotel` como `UNCHANGED` y no añade historial

### Requirement: Conservar el último estado válido
Si una entrada trae `error` o no pasa la validación, el backend SHALL conservar el último estado guardado del proyecto, guardar el error con su fecha y exponerlo. El backend MUST NOT reemplazar los datos por listas vacías. Si lo que falla es un cambio OpenSpec concreto (`{name, error}`), SHALL conservarse la última versión válida de ese cambio y aplicarse el resto de la entrada.

#### Scenario: Repo ilegible
- **WHEN** la entrada de `distriapp` llega con `error: "ROADMAP.md: permiso denegado"`
- **THEN** el dashboard sigue mostrando el último estado de `distriapp`, con el aviso del error y su fecha

#### Scenario: Un cambio ilegible
- **WHEN** el cambio `kids` de `nolost` llega con `error` y el resto de la entrada es válida
- **THEN** el resto de `nolost` se actualiza y `kids` conserva sus tareas previas, con el aviso del error

### Requirement: Proyectos ausentes no se borran
Si un proyecto guardado no aparece en un snapshot válido, el backend SHALL marcarlo con `missingSince` (la fecha del primer snapshot en que faltó) y conservar sus datos. Si vuelve a aparecer, `missingSince` SHALL limpiarse.

#### Scenario: Repo renombrado o fuera del workspace
- **WHEN** `contact` no aparece en el snapshot
- **THEN** `contact` sigue visible, con el aviso "no reportado desde <fecha>"

### Requirement: Validación por entrada
El backend SHALL validar cada entrada de forma independiente. Una entrada inválida (falta `name`, `state` fuera del enum, `priority_rank` < 1, estructura de `roadmap` u `openspec` incorrecta) SHALL rechazarse con su motivo en el sync run, sin afectar al resto. Un bloqueo inválido SHALL descartarse solo, como aviso, sin rechazar la entrada.

#### Scenario: Estado inválido
- **WHEN** una entrada trae `state: "NOW"`
- **THEN** esa entrada se rechaza con motivo "state inválido", y las demás se aplican

### Requirement: El sync es de solo lectura sobre los repos
Ni el generador ni el backend SHALL escribir, mover o reformatear archivos de ningún repositorio, incluido `openspec/`.

#### Scenario: Sync completo
- **WHEN** se ejecuta el generador sobre todos los repos
- **THEN** `git status` de cada repo no muestra cambios causados por el sync
