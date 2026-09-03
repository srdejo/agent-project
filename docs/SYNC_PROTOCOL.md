# SYNC_PROTOCOL.md

Contrato entre OpenClaw (u otro agente, corriendo en la máquina del desarrollador) y el backend de `agent-project` para mantener el dashboard actualizado. El backend **no** hace polling de Git ni parsea Markdown — solo lee JSON que otro proceso deposita en su inbox.

## Cómo saber si el backend está vivo antes de subir nada

`agent-project` corre en `nolost-vps` **desplegado y en vivo** desde 2026-08-19 como servicio `systemd` (`agent-project.service`, puerto `8083` loopback) — **no** como contenedor Docker, no hay nada que "levantar" aparte de eso. Antes de asumir que no está desplegado, chequear:

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://agent.srdejo.com.co/api/projects   # 200 = vivo
ssh srdejo@nolost-vps "systemctl is-active agent-project"                           # active = corriendo
```

Si está `active`/`200`, alcanza con subir los archivos al inbox — no hace falta desplegar ni reiniciar nada. La detección es reactiva (`WatchService` sobre `inboxDir`, ver `InboxSyncJob`): apenas se crea o modifica `progreso.json`/`nuevo.json` en el inbox, el backend lo procesa (con un debounce de ~400ms para coalescer ambos archivos en un solo ciclo si llegan casi juntos).

## Consultar el API antes de clasificar progreso.json vs nuevo.json

Antes de armar los dos archivos, consultar `GET /api/projects` (`https://agent.srdejo.com.co/api/projects`) para saber qué ids ya existen en la base:

```bash
curl -s https://agent.srdejo.com.co/api/projects
```

La respuesta trae `projects[].id` — esa es la lista real de proyectos ya creados. Cualquier proyecto de `docs/` (o `README.md`, si `docs/` no existe o está vacía) cuyo id **no** aparezca ahí va en `nuevo.json`; los que sí aparezcan van en `progreso.json`. No confiar en memoria de sesiones anteriores ni asumir la lista — el API es la fuente de verdad, puede haber cambiado (proyectos creados manualmente, borrados, etc.).

## Dos archivos fijos, no uno por proyecto

En cada corrida, OpenClaw barre la carpeta `docs/` de todos los proyectos registrados y genera **como máximo dos archivos**, cada uno un mapa `id de proyecto -> datos`:

- **`progreso.json`** — actualizaciones para proyectos que **ya existen** en la base.
- **`nuevo.json`** — proyectos que **todavía no existen** y hay que crear.

```
<deploy-dir>/data/inbox/progreso.json
<deploy-dir>/data/inbox/nuevo.json
```

Rutas reales:
- **Prod (`nolost-vps`)**: `/home/srdejo/agent-project/data/inbox/progreso.json` — `WorkingDirectory` del `systemd` unit apunta directo ahí (ver `docs/DEPLOYMENT.md`).
- **Local (`gradlew bootRun`)**: `agent-project/backend/bootstrap/data/inbox/progreso.json` — el working dir de `bootRun` es el módulo `bootstrap`, no la raíz de `backend/`. (El path `backend/data/inbox/` que documentaba una versión anterior de este archivo estaba mal.)

El backend reacciona en cuanto alguno de los dos archivos aparece en el inbox (`WatchService`, sin polling). Se procesa **entero** (cada entrada del mapa se valida y aplica de forma independiente — una entrada mala no bloquea al resto) y **se borra siempre al terminar**, haya habido cambios o no. OpenClaw regenera estos archivos frescos en cada una de sus propias corridas; el histórico real vive en la tabla `project_snapshots`, no en el filesystem.

## Cómo subirlos de forma atómica

El backend puede estar leyendo el directorio mientras se sube un archivo. Mismo patrón que `nolost/deploy.ps1`, aplicado a cada archivo:

```powershell
scp progreso.json nolost-vps:/home/srdejo/agent-project/data/inbox/progreso.json.tmp
ssh nolost-vps "mv /home/srdejo/agent-project/data/inbox/progreso.json.tmp /home/srdejo/agent-project/data/inbox/progreso.json"

scp nuevo.json nolost-vps:/home/srdejo/agent-project/data/inbox/nuevo.json.tmp
ssh nolost-vps "mv /home/srdejo/agent-project/data/inbox/nuevo.json.tmp /home/srdejo/agent-project/data/inbox/nuevo.json"
```

## Esquema JSON

Mapa cuya clave es el **id del proyecto** (ya no va como campo `id` adentro del objeto). Campos requeridos por entrada: `name`, `repo`, `progress` (0–100), `status`, `verify`, `last_modified`. El resto son opcionales — si faltan, se normalizan a `null` o lista vacía. `status` debe ser uno de `IN_PROGRESS | BLOCKED | STARTED | COMPLETED`; `verify` uno de `PASSED | ATTENTION | PENDING`. `last_modified` es un datetime ISO-8601 con offset (ej. `2026-08-19T18:00:00-05:00`). `priority`, si viene, debe ser uno de `NOW | NEXT | DECIDE | ON_TRACK | FROZEN`. `priority_rank`, si viene, debe ser un **entero mayor o igual a 1**: si no es un número entero (una cadena `"2"`, un decimal `2.5`, un booleano) o es menor que 1, la entrada se descarta.

> ⚠️ **Dos campos `status` distintos, no confundirlos.** El `status` del proyecto (raíz del objeto) es el enum en **MAYÚSCULAS** `IN_PROGRESS|BLOCKED|STARTED|COMPLETED` y solo describe el estado general del proyecto en el detalle — **no alimenta ningún contador del listado**. El `status` de cada tarea (`tasks[].status`) es un enum aparte en **minúsculas** `done|wip|blocked|todo`, y es el único que suma al contador **"BLOCKED"** (y a "VERIFIED TASKS") del dashboard — ver `ProjectQueryService.countByStatus`, que compara el string tal cual, sin normalizar mayúsculas/minúsculas. Si el proyecto está bloqueado pero ninguna tarea tiene `"status": "blocked"`, el contador queda en 0 aunque `status: "BLOCKED"` esté puesto en la raíz. Y si por error se escribe `"BLOCKED"` (mayúscula) dentro de `tasks[]`, esa tarea se descarta por completo (ver [Reglas de validación](#reglas-de-validación-por-archivo)) y tampoco cuenta.

```json
{
  "nolost": {
    "last_modified": "2026-08-19T18:00:00-05:00",
    "name": "Mi Casa Church",
    "repo": "nolost",
    "progress": 42,
    "stage": "Fase 3 — Mentoreo MVP",
    "status": "IN_PROGRESS",
    "updated": "19 Aug 12:00",
    "commit": "8413025",
    "verify": "PASSED",
    "summary": "Plataforma de gestión para la iglesia: consolidación de miembros, mentoreo y asistencia.",
    "alias": "Mi Casa · Consolidación",
    "priority": "NOW",
    "priority_rank": 1,
    "open_question": "¿Quién valida los datos de consolidación antes de migrarlos?",
    "stack": ["Node", "React", "PostgreSQL"],
    "tasks": [
      { "name": "Consolidación de miembros", "stage": "Fase 3", "status": "done", "date": "18 Ago", "commit": "8413025" },
      { "name": "Toma de asistencia móvil", "stage": "Fase 3", "status": "wip", "date": "19 Ago", "commit": "—" }
    ],
    "checks": [{ "name": "./gradlew test", "ok": true, "duration": "12s" }],
    "events": [{ "time": "12:00", "mark": "✓", "text": "Progress synchronization" }]
  },
  "hotel": {
    "last_modified": "2026-08-19T17:45:00-05:00",
    "name": "Hotel Management",
    "repo": "hotel-management",
    "progress": 68,
    "status": "IN_PROGRESS",
    "verify": "PASSED"
  }
}
```

| Campo | Tipo | Requerido | Notas |
|---|---|---|---|
| (clave del mapa) | string | sí | Id estable del proyecto. |
| `last_modified` | datetime ISO-8601 | sí | Metadata de última modificación — el backend compara este valor contra el guardado para decidir si actualiza (ver más abajo). No es la fecha del sync, es la del estado que describe. |
| `name` | string | sí | Nombre mostrado en el dashboard. |
| `repo` | string | sí | Nombre corto del repositorio. |
| `progress` | int 0–100 | sí | Porcentaje de avance. |
| `stage` | string | no | Etapa/fase actual, texto libre. |
| `status` | enum | sí | `IN_PROGRESS \| BLOCKED \| STARTED \| COMPLETED`. |
| `updated` | string | no | Etiqueta de última actualización (texto libre). |
| `commit` | string | no | SHA corto del commit que originó este estado. |
| `verify` | enum | sí | `PASSED \| ATTENTION \| PENDING`. |
| `summary` | string | no | Descripción del proyecto en 1–2 frases, para la sección "Qué es este proyecto" del detalle. |
| `alias` | string | no | Subtítulo corto del proyecto, máx. 120 caracteres (ej. `SCI 360 · Multimarcasa`). Capa editorial: **no se deriva de nada**, lo decide el usuario. Un alias más largo que 120 caracteres descarta la entrada. |
| `priority` | enum | no | `NOW \| NEXT \| DECIDE \| ON_TRACK \| FROZEN`. Prioridad editorial del proyecto dentro del portafolio. Un valor fuera del enum descarta la entrada, igual que `status`/`verify`. |
| `priority_rank` | int ≥ 1 | no | Posición del proyecto en el orden de prioridad del portafolio (`1` = el primero que se atiende). Capa editorial, igual que `priority`, pero responde otra pregunta: `priority` dice **en qué grupo de decisión está** el proyecto, `priority_rank` dice **en qué orden se atiende**. Un valor que no sea entero, o menor que 1, descarta la entrada. |
| `open_question` | string | no | La única pregunta sin responder que desbloquea el proyecto. Texto libre largo, también editorial. |
| `stack` | string[] | no | Tecnologías/stack del proyecto (tags en el detalle). |
| `tasks` | `{name, stage, status, date, commit}[]` | no | Tareas individuales del roadmap. `status` uno de `done \| wip \| blocked \| todo` (**minúsculas, distinto del `status` del proyecto**) — **`done` solo si hay evidencia de verificación real, nunca porque se escribió código** (misma regla que `progress`, ver más abajo). Es el campo que alimenta la tabla "Tareas desarrolladas" del detalle y los conteos de "BLOCKED"/"VERIFIED TASKS" del listado — el `status` del proyecto no cuenta para esas cifras. |
| `checks` | `{name, ok, duration}[]` | no | Última corrida de verificación. |
| `events` | `{time, mark, text}[]` | no | Feed de actividad reciente del agente. |

`project_snapshots` (histórico) lo calcula el backend — no va en el JSON.

## Capa editorial: `alias`, `priority`, `priority_rank`, `open_question`

Estos cuatro campos son la **capa editorial** del portafolio: no se derivan del repo, del roadmap ni de Git — los decide el usuario. Los cuatro son opcionales y anulables, y por eso siguen una regla distinta a la del resto de campos:

- **Si el campo no viene en el JSON** → se **conserva** el valor que ya tenía el proyecto en la base. Un `progreso.json` que no los traiga sigue siendo válido y no borra nada.
- **Si el campo viene explícitamente como `null`** (o como cadena vacía) → se **limpia** el valor guardado.

Es decir, "ausente" y "`null`" **no** significan lo mismo: omitir el campo es "no lo toques", mandarlo en `null` es "bórralo". El parser distingue ambos casos (`node.has(campo)` vs. `node.get(campo).isNull()`), así que para vaciar un alias hay que mandarlo explícitamente en `null`, no simplemente dejar de enviarlo.

`priority` solo se valida contra el enum cuando viene presente y no es `null`; un valor fuera de `NOW | NEXT | DECIDE | ON_TRACK | FROZEN` descarta la entrada completa con warning, sin bloquear al resto del archivo.

Los cuatro se exponen en el API como `alias`, `priority`, `priorityRank` y `openQuestion` (camelCase en la salida JSON, igual que `tasksDone`/`tasksTotal`), tanto en el listado (`GET /api/projects`) como en el detalle (`GET /api/projects/{id}`).

### `priority` es el grupo, `priority_rank` es el orden

Son dos campos distintos y **no se reemplazan entre sí**:

- **`priority`** (`NOW | NEXT | DECIDE | ON_TRACK | FROZEN`) es el **grupo de decisión**: en qué estado está el proyecto dentro del portafolio (se está trabajando ahora, entra después, falta decidir algo, va en carril, está congelado).
- **`priority_rank`** (entero ≥ 1) es la **posición en la fila**: en qué orden se atiende, siendo `1` el primero. Dos proyectos pueden compartir grupo (`NOW` los dos) y aun así tener un orden claro entre ellos (`1` y `2`).

El backend **no** valida que los rangos sean únicos ni consecutivos, ni que sean coherentes con el grupo — eso corre por cuenta de quien genera el JSON. Lo único que valida es que, si el campo viene, sea un entero ≥ 1.

### Cómo se limpia un `priority_rank`

`alias`, `priority` y `open_question` son cadenas, así que "vacío" (`""`) alcanza como marca interna de "límpialo". `priority_rank` es un entero y no tiene cadena vacía, así que usa **`0`** como esa misma marca: un valor fuera de su propio dominio, ya que un rango válido siempre arranca en `1`. Esto es interno del backend — desde el JSON la regla es la misma que para los otros tres:

- `priority_rank` **ausente** → se conserva el rango guardado.
- `"priority_rank": null` → se **borra** el rango guardado.
- `"priority_rank": 0` (o negativo) → **descarta la entrada** con warning. Para borrar el rango hay que mandar `null`, no `0`.

## Reglas de validación por archivo

- **`progreso.json`**: cada clave debe ser un id **ya existente**. Si no existe, esa entrada se descarta (log de warning) — no crea proyectos. Si existe: se compara `last_modified` recibido contra el guardado; igual → no se toca la base; distinto → se aplica el update y se guarda una nueva fila en `project_snapshots`.
- **`nuevo.json`**: cada clave debe ser un id que **no existe todavía**. Si ya existe, esa entrada se descarta (log de warning) — no pisa proyectos existentes vía este archivo. Si no existe, se crea.

Una entrada con campos inválidos (falta un requerido, `status`/`verify`/`priority` fuera del enum, `progress` fuera de 0–100, `priority_rank` que no sea entero o sea menor que 1, `alias` de más de 120 caracteres, `last_modified` no parseable, o algún `tasks[].status` fuera de `done|wip|blocked|todo`) también se descarta con warning — no bloquea al resto de las entradas del archivo.

## Regla de negocio: nunca inventar progreso

> No se actualiza el porcentaje porque el agente escribió código. Se actualiza cuando existe evidencia de avance (tarea del roadmap completa + verificación real). La misma regla aplica a `tasks[].status = "done"`: una tarea se marca `done` solo con evidencia real, nunca porque el código fue escrito.

El backend no valida esto — es responsabilidad de quien genera el JSON (OpenClaw) no reportar `progress` ni `tasks[].status: "done"` sin evidencia real.

## Regla de negocio: `blocked` también aplica por falta de definición, no solo por impedimento externo

`tasks[].status = "blocked"` no es solo "algo externo me detuvo" (credenciales, API caída, dependencia de otro equipo). También aplica cuando, al leer el roadmap (`ROADMAP.md`/`TASKS.md`), la tarea **no tiene criterio de aceptación o alcance claro** para poder ejecutarla o verificarla — p. ej. un ítem del roadmap escrito de forma ambigua, sin definir qué significa "terminado" para esa tarea, o sin la validación/prueba que demuestre que se completó.

> Si OpenClaw no puede determinar con qué evidencia verificar una tarea, esa tarea se marca `blocked` — nunca se infiere `done` "porque probablemente es lo que se quiso decir", ni se deja en `wip` indefinidamente sin señal de por qué no avanza.

Esto es una extensión de la regla anterior (nunca inventar progreso): la ambigüedad en la definición de la tarea es, en sí misma, un bloqueador tan real como uno externo, y debe quedar visible en el dashboard igual que cualquier otro. Registrar la causa en `docs/PROGRESS.md`/`ROADMAP.md` del proyecto (sección "Bloqueadores") para que quede trazable de dónde viene.

## Fuera de alcance de este documento

Cómo OpenClaw lee `ROADMAP.md`/`PROGRESS.md`/`TASKS.md` y el estado de Git de cada proyecto para construir `progreso.json`/`nuevo.json`, y cómo se programa su envío periódico, corre por cuenta del usuario en su propia máquina — este documento solo define el contrato que el backend espera recibir.
