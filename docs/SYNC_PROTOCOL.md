# SYNC_PROTOCOL.md

Contrato entre OpenClaw (u otro agente, corriendo en la máquina del desarrollador) y el backend de `agent-project` para mantener el dashboard actualizado. El backend **no** hace polling de Git ni parsea Markdown — solo lee JSON que otro proceso deposita en su inbox.

## Dónde depositar el archivo

Un archivo JSON por proyecto, en el directorio inbox del backend en `nolost-vps`:

```
<deploy-dir>/data/inbox/<project-id>.json
```

Ejemplo: `/home/srdejo/agent-project/data/inbox/hotel.json`.

El backend escanea ese directorio cada `SYNC_POLL_INTERVAL_MS` (default 5 minutos, configurable por variable de entorno). Tras procesar un archivo, lo mueve a `data/inbox/processed/<id>-<timestamp>-<archivo>.json` (éxito, con o sin cambios) o a `data/inbox/rejected/<timestamp>-<archivo>.json` (JSON inválido) — nunca lo borra silenciosamente.

## Cómo subirlo de forma atómica

El backend puede estar leyendo el directorio mientras se sube el archivo. Para evitar una lectura parcial:

```powershell
scp hotel.json nolost-vps:/home/srdejo/agent-project/data/inbox/hotel.json.tmp
ssh nolost-vps "mv /home/srdejo/agent-project/data/inbox/hotel.json.tmp /home/srdejo/agent-project/data/inbox/hotel.json"
```

Mismo patrón que ya usa `nolost/deploy.ps1` para sus propios despliegues.

## Esquema JSON

Campos requeridos: `id`, `name`, `repo`, `progress` (0–100), `status`, `verify`. El resto son opcionales — si faltan, se normalizan a `null` o lista vacía. `status` debe ser uno de `IN_PROGRESS | BLOCKED | STARTED | COMPLETED`; `verify` uno de `PASSED | ATTENTION | PENDING`. Un archivo que no cumpla esto se rechaza completo (no se aplica parcialmente).

```json
{
  "id": "hotel",
  "name": "Hotel Management",
  "repo": "hotel-management",
  "progress": 68,
  "stage": "Stage 8 — Remaining domain modules",
  "status": "IN_PROGRESS",
  "updated": "18 Aug 22:30",
  "commit": "a82f19d",
  "verify": "PASSED",
  "completed": ["front-desk", "housekeeping"],
  "next": ["shop", "restaurant"],
  "blocked": ["login multi-tenant ambiguity"],
  "checks": [
    { "name": "./gradlew test", "ok": true, "duration": "1m 08s" }
  ],
  "events": [
    { "time": "22:30", "mark": "✓", "text": "Progress synchronization — 64% → 68%" }
  ]
}
```

| Campo | Tipo | Requerido | Notas |
|---|---|---|---|
| `id` | string | sí | Identificador estable del proyecto (usado también como nombre de archivo). Si no existe, se crea. |
| `name` | string | sí | Nombre mostrado en el dashboard. |
| `repo` | string | sí | Nombre corto del repositorio (ej. `hotel-management`). |
| `progress` | int 0–100 | sí | Porcentaje de avance. |
| `stage` | string | no | Etapa/fase actual, texto libre. |
| `status` | enum | sí | `IN_PROGRESS \| BLOCKED \| STARTED \| COMPLETED`. |
| `updated` | string | no | Etiqueta de última actualización (texto libre, no se parsea como fecha). |
| `commit` | string | no | SHA corto del commit que originó este estado. |
| `verify` | enum | sí | `PASSED \| ATTENTION \| PENDING`. |
| `completed` | string[] | no | Tareas completadas y verificadas. |
| `next` | string[] | no | Próximas tareas. |
| `blocked` | string[] | no | Bloqueadores activos. |
| `checks` | `{name, ok, duration}[]` | no | Última corrida de verificación (tests, build, etc). |
| `events` | `{time, mark, text}[]` | no | Feed de actividad reciente del agente. |

`last_sync_hash` y el snapshot histórico (`project_snapshots`) los calcula el backend — no van en el JSON.

## Regla de negocio: nunca inventar progreso

> No se actualiza el porcentaje porque el agente escribió código. Se actualiza cuando existe evidencia de avance (tarea del roadmap completa + verificación real).

Mismo principio que `docs/OPENCLAW_PROJECT_CONTROL_CENTER.md` §9. El backend no valida esto — es responsabilidad de quien genera el JSON (OpenClaw) no reportar `progress` sin evidencia real.

## Fuera de alcance de este documento

Cómo OpenClaw lee `ROADMAP.md`/`PROGRESS.md`/`TASKS.md` y el estado de Git de cada proyecto para construir este JSON, y cómo se programa su envío periódico, corre por cuenta del usuario en su propia máquina — este documento solo define el contrato que el backend espera recibir.
