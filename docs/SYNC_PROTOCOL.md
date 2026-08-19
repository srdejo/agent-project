# SYNC_PROTOCOL.md

Contrato entre OpenClaw (u otro agente, corriendo en la máquina del desarrollador) y el backend de `agent-project` para mantener el dashboard actualizado. El backend **no** hace polling de Git ni parsea Markdown — solo lee JSON que otro proceso deposita en su inbox.

## Dos archivos fijos, no uno por proyecto

En cada corrida, OpenClaw barre la carpeta `docs/` de todos los proyectos registrados y genera **como máximo dos archivos**, cada uno un mapa `id de proyecto -> datos`:

- **`progreso.json`** — actualizaciones para proyectos que **ya existen** en la base.
- **`nuevo.json`** — proyectos que **todavía no existen** y hay que crear.

```
<deploy-dir>/data/inbox/progreso.json
<deploy-dir>/data/inbox/nuevo.json
```

Ejemplo real (working dir de `bootRun`/el jar en prod): `agent-project/backend/bootstrap/data/inbox/progreso.json`. (El path `backend/data/inbox/` que documentaba una versión anterior de este archivo estaba mal — `gradlew bootRun`/el jar corren con working dir en el módulo `bootstrap`.)

El backend escanea el inbox cada `SYNC_POLL_INTERVAL_MS` (default 6 horas — pensado para la cadencia con la que corre OpenClaw, no para reaccionar al instante). Si alguno de los dos archivos existe, se procesa **entero** (cada entrada del mapa se valida y aplica de forma independiente — una entrada mala no bloquea al resto) y **se borra siempre al terminar**, haya habido cambios o no. OpenClaw regenera estos archivos frescos en cada una de sus propias corridas; el histórico real vive en la tabla `project_snapshots`, no en el filesystem.

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
    "completed": ["Consolidación"],
    "next": ["Toma de asistencia móvil"],
    "blocked": [],
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
| `completed` | string[] | no | Tareas completadas y verificadas. |
| `next` | string[] | no | Próximas tareas. |
| `blocked` | string[] | no | Bloqueadores activos. |
| `checks` | `{name, ok, duration}[]` | no | Última corrida de verificación. |
| `events` | `{time, mark, text}[]` | no | Feed de actividad reciente del agente. |

`project_snapshots` (histórico) lo calcula el backend — no va en el JSON.

## Reglas de validación por archivo

- **`progreso.json`**: cada clave debe ser un id **ya existente**. Si no existe, esa entrada se descarta (log de warning) — no crea proyectos. Si existe: se compara `last_modified` recibido contra el guardado; igual → no se toca la base; distinto → se aplica el update y se guarda una nueva fila en `project_snapshots`.
- **`nuevo.json`**: cada clave debe ser un id que **no existe todavía**. Si ya existe, esa entrada se descarta (log de warning) — no pisa proyectos existentes vía este archivo. Si no existe, se crea.

Una entrada con campos inválidos (falta un requerido, `status`/`verify` fuera del enum, `progress` fuera de 0–100, `last_modified` no parseable) también se descarta con warning — no bloquea al resto de las entradas del archivo.

## Regla de negocio: nunca inventar progreso

> No se actualiza el porcentaje porque el agente escribió código. Se actualiza cuando existe evidencia de avance (tarea del roadmap completa + verificación real).

El backend no valida esto — es responsabilidad de quien genera el JSON (OpenClaw) no reportar `progress` sin evidencia real.

## Fuera de alcance de este documento

Cómo OpenClaw lee `ROADMAP.md`/`PROGRESS.md`/`TASKS.md` y el estado de Git de cada proyecto para construir `progreso.json`/`nuevo.json`, y cómo se programa su envío periódico, corre por cuenta del usuario en su propia máquina — este documento solo define el contrato que el backend espera recibir.
