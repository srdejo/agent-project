# Design

## Context

- El snapshot (`docs/SYNC_PROTOCOL.md`, v2) lo genera `infra/portfolio/portfolio.py` en la raíz del workspace. Trae
  ROADMAP, OpenSpec y la capa editorial, pero ni commits ni historial. `infra/portfolio/daily.py` ya calcula "qué se
  hizo", pero solo en la consola de Daniel.
- `SnapshotParser` ignora las claves de primer nivel que no conoce y exige `format_version == 2`.
- `SyncSnapshotUseCase` detecta cambios por el hash de la entrada (más `RULE_VERSION`) y reemplaza el proyecto completo
  si cambió. Las tareas se borran y se vuelven a insertar, así que su `id` no sirve para comparar antes y después.
- `ProgressCalculator` ya deriva `BLOCKED` con `blockedReason` (por "falta de definición"). `WorkUseCase` toma como
  siguiente tarea de un cambio la primera `OPEN`, y `WorkRules` solo considera accionables las `OPEN`/`IN_PROGRESS`.
  Por eso marcar una tarea `BLOCKED` ya la saca de "Continuar", de "Accionables" y del daily.
- La regla de avance vive solo en `ProgressCalculator`; si cambia, sube `RULE_VERSION`.

## Goals / Non-Goals

**Goals:**
- Que "Hecho" salga de datos que entran solos con el sync, sin escribir nada a mano.
- No romper un generador viejo ni un backend viejo: los dos tienen que tolerar el campo nuevo.

**Non-Goals:**
- Mostrar diffs o archivos de cada commit.
- Usar `daily-base.json` (el corte local de `daily.py`) como fuente: el backend define su propio período.
- Cambiar `daily.py`: sigue siendo la herramienta de consola.
- El presupuesto de uso (`daily-con-presupuesto`).

## Decisions

### D1. Commits en un bloque `activity` de primer nivel, fuera de las entradas
La lista de commits de 7 días cambia aunque el repo no cambie: cada día salen commits viejos de la ventana. Si viajara
dentro de la entrada, entraría en el hash y los proyectos sin trabajo nuevo quedarían `UPDATED` y se reemplazarían
enteros. Un commit nuevo ya hace `UPDATED` al proyecto por `source_commit`, y eso no cambia. En un bloque aparte, la
lista no toca la detección de cambios.

Alternativa descartada: subir `format_version` a 3 con commits por entrada y excluirlos del hash. Obliga a desplegar el
backend y el generador en un orden fijo y complica el hash, sin ganar nada.

Con un backend viejo, el bloque se ignora. Con un generador viejo, el backend nuevo conserva los commits que tenía.

### D2. Ventana del generador: 7 días, 30 commits por proyecto
`git log --all --no-merges --since=7.days --format=%h%x1f%aI%x1f%s`, sin duplicados por SHA. Siete días cubren un fin de
semana largo, y 30 commits bastan para mostrar 8 por proyecto. `--all` incluye las ramas `claude/*` sin mergear, que
también son trabajo hecho.

### D3. Tabla `repo_commit`, reemplazada por proyecto
`repo_commit(project_id, sha, committed_at, subject)` con PK `(project_id, sha)`. Por cada proyecto presente en
`activity.projects` se borra y se vuelve a insertar, en una transacción por proyecto. No lleva FK a `project`: un
commit puede llegar de un proyecto que todavía no está guardado, y se descarta al leer si el proyecto no existe.

### D4. Tareas cerradas por diff en el backend, tabla `task_closed`
El generador reporta hechos y el backend decide. Antes de reemplazar un proyecto `UPDATED`, el use case lee sus tareas
guardadas y las compara con las nuevas por una clave estable:
- ROADMAP: `R|<stage_key o título>|<texto>`;
- OpenSpec: `O|<cambio>|<número o texto>`.

Cada clave que pasa de abierta (sin marcar y no tachada) a `checked && !cancelled` se inserta en
`task_closed(id, project_id, source, scope, number, text, closed_at)`, con `closed_at = generated_at`. Las claves que
no existían antes no cuentan (un cambio nuevo ya avanzado no se registra), ni los proyectos `CREATED`. La tabla es un
historial: nunca se borra en el sync.

Alternativa descartada: calcularlo en el generador con `daily-base.json`. Depende de un archivo local que solo existe
en la máquina de Daniel y se pierde si el sync corre en otra.

### D5. Marcador de bloqueo en `ProgressCalculator`
Regex sin distinguir mayúsculas, aplicada a tareas abiertas de los dos orígenes:
`\(?\bbloquead[ao]\b(?:\s*\([^)]*\))?\s*:\s*(.+)`. El motivo se corta en el primer `. `, en `)` si el marcador abrió
paréntesis, y a 200 caracteres. Queda en estado `BLOCKED` con ese `blockedReason`. Para distinguirlo de "falta de
definición", el daily filtra por el texto de ese motivo, que es una constante (`UNDEFINED_REASON`).

`RULE_VERSION` pasa a `2`, así el siguiente sync recalcula todo (requisito "Recalcular los proyectos ya guardados"). El
diff de D4 no registra nada en ese recálculo, porque `checked` no cambia.

### D6. Período de "Hecho" en el backend
`doneSince` = 00:00 Bogotá del día más reciente anterior a hoy con actividad (un commit o un `task_closed`) dentro de
los últimos 7 días. Si no hay ninguno, es hoy a las 00:00. Se calcula sobre lo guardado y la fecha de `Clock`, así que es
determinista. Un nuevo puerto `IActivityPersistencePort` expone `commitsSince(Instant)` y `closedSince(Instant)`, y las
fechas de actividad de los últimos 7 días.

### D7. Forma de la respuesta
```
done: [{projectId, projectName, tasks: [{scope, number, text}], commits: [{sha, subject}], moreCommits}]
blockers: [{projectId, projectName, kind: EDITORIAL|TASK, type, description, owner, scope, number, taskText}]
```
Para `kind: TASK`, `type` es `null`, `description` es el motivo, `scope`/`number` ubican la tarea y `taskText` es su
texto recortado a 100 caracteres. Dentro de un proyecto, las tareas bloqueadas salen en el orden en que están guardadas:
ROADMAP primero y después OpenSpec. Los proyectos
`PAUSED`/`COMPLETED`/`ARCHIVED`/`MAINTENANCE` quedan fuera de `blockers`, pero no de `done`: si alguien commiteó en un
proyecto en mantenimiento, eso se hizo.

## Risks / Trade-offs

- [Un `[x]` sin commitear se registra como cerrado y después se desmarca] → El registro queda. Es aceptable: el daily
  refleja lo que dijo el repo en ese sync. No se agrega lógica de "desmarcado".
- [Cambiar el texto de una tarea ya cerrada cambia su clave] → Ninguna de las dos claves cumple "antes abierta, ahora
  cerrada", así que no se registra nada. Se pierde ese cierre, que es raro.
- [El marcador atrapa una frase que no era un bloqueo] → Exige dos puntos después de "bloqueada", y hay un escenario
  de spec para la mención sin marcador.
- [Muchos commits de un repo dominan "Hecho"] → Máximo 8 por proyecto, con "N más".

## Migration Plan

1. Archivar `daily-y-limpieza`, para que `daily-plan` exista como spec principal.
2. Desplegar el backend (Flyway `V8` crea `repo_commit` y `task_closed`) y el frontend juntos.
3. Correr `sync-portfolio.ps1` con el `portfolio.py` nuevo. El primer sync recalcula todo por `RULE_VERSION` 2 y llena
   los commits. Las tareas cerradas empiezan a registrarse desde ese sync: "Hecho" muestra solo commits hasta el
   siguiente cierre.
4. Rollback: volver al jar anterior. Las tablas nuevas sobran sin molestar y el generador nuevo sigue siendo compatible
   (D1).
