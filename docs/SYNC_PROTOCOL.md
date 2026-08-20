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

Mapa cuya clave es el **id del proyecto** (ya no va como campo `id` adentro del objeto). Campos requeridos por entrada: `name`, `repo`, `progress` (0–100), `status`, `verify`, `last_modified`. El resto son opcionales — si faltan, se normalizan a `null` o lista vacía. `status` debe ser uno de `IN_PROGRESS | BLOCKED | STARTED | COMPLETED`; `verify` uno de `PASSED | ATTENTION | PENDING`. `last_modified` es un datetime ISO-8601 con offset (ej. `2026-08-19T18:00:00-05:00`).

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
| `stack` | string[] | no | Tecnologías/stack del proyecto (tags en el detalle). |
| `tasks` | `{name, stage, status, date, commit}[]` | no | Tareas individuales del roadmap. `status` uno de `done \| wip \| blocked \| todo` — **`done` solo si hay evidencia de verificación real, nunca porque se escribió código** (misma regla que `progress`, ver más abajo). Alimenta la tabla "Tareas desarrolladas" del detalle y los conteos de tareas/bloqueados/verificadas del listado. |
| `checks` | `{name, ok, duration}[]` | no | Última corrida de verificación. |
| `events` | `{time, mark, text}[]` | no | Feed de actividad reciente del agente. |

`project_snapshots` (histórico) lo calcula el backend — no va en el JSON.

## Reglas de validación por archivo

- **`progreso.json`**: cada clave debe ser un id **ya existente**. Si no existe, esa entrada se descarta (log de warning) — no crea proyectos. Si existe: se compara `last_modified` recibido contra el guardado; igual → no se toca la base; distinto → se aplica el update y se guarda una nueva fila en `project_snapshots`.
- **`nuevo.json`**: cada clave debe ser un id que **no existe todavía**. Si ya existe, esa entrada se descarta (log de warning) — no pisa proyectos existentes vía este archivo. Si no existe, se crea.

Una entrada con campos inválidos (falta un requerido, `status`/`verify` fuera del enum, `progress` fuera de 0–100, `last_modified` no parseable, o algún `tasks[].status` fuera de `done|wip|blocked|todo`) también se descarta con warning — no bloquea al resto de las entradas del archivo.

## Regla de negocio: nunca inventar progreso

> No se actualiza el porcentaje porque el agente escribió código. Se actualiza cuando existe evidencia de avance (tarea del roadmap completa + verificación real). La misma regla aplica a `tasks[].status = "done"`: una tarea se marca `done` solo con evidencia real, nunca porque el código fue escrito.

El backend no valida esto — es responsabilidad de quien genera el JSON (OpenClaw) no reportar `progress` ni `tasks[].status: "done"` sin evidencia real.

## Fuera de alcance de este documento

Cómo OpenClaw lee `ROADMAP.md`/`PROGRESS.md`/`TASKS.md` y el estado de Git de cada proyecto para construir `progreso.json`/`nuevo.json`, y cómo se programa su envío periódico, corre por cuenta del usuario en su propia máquina — este documento solo define el contrato que el backend espera recibir.
