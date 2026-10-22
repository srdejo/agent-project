# SYNC_PROTOCOL.md

Contrato entre el generador del portafolio (`infra/portfolio/portfolio.py`, que corre en la máquina del desarrollador) y el backend de `agent-project`. El backend no clona repos ni lee Markdown: solo procesa el snapshot JSON que el generador deposita en su inbox.

**Formato vigente: v2** (desde 2026-10-15, cambio OpenSpec `control-center-fase-1`). Reemplaza a `progreso.json`/`nuevo.json`, que el backend ya no procesa (ver [Formato obsoleto](#formato-obsoleto)).

## Comprobar que el backend está vivo

`agent-project` corre en `nolost-vps` como servicio `systemd` (`agent-project.service`, puerto `8083` loopback), detrás de oauth2-proxy.

```bash
ssh srdejo@nolost-vps "systemctl is-active agent-project"                 # active = corriendo
ssh srdejo@nolost-vps "curl -s http://127.0.0.1:8083/api/health/sync"      # último sync y antigüedad
```

## Un archivo por corrida: `snapshot.json`

Cada corrida del generador produce **un solo archivo** con el estado completo de todos los repos:

```
<deploy-dir>/data/inbox/snapshot.json
```

- **Prod**: `/home/srdejo/agent-project/data/inbox/snapshot.json`
- **Local** (`gradlew bootRun`): `agent-project/backend/bootstrap/data/inbox/snapshot.json`, porque el directorio de trabajo de `bootRun` es `bootstrap/`.

El backend detecta el archivo al instante (`WatchService` con debounce de ~400 ms), lo procesa entero y **lo borra al terminar**, haya o no cambios. El histórico vive en la base de datos, no en el filesystem.

### Subida atómica

El backend puede estar leyendo el directorio mientras llega el archivo, así que se sube con nombre temporal y se renombra:

```powershell
scp snapshot.json nolost-vps:/home/srdejo/agent-project/data/inbox/snapshot.json.tmp
ssh nolost-vps "mv /home/srdejo/agent-project/data/inbox/snapshot.json.tmp /home/srdejo/agent-project/data/inbox/snapshot.json"
```

## Principio: el generador reporta hechos, el backend decide

El generador **no calcula** porcentajes, estados de tarea ni estado del proyecto. Envía lo que hay en los archivos: checkboxes, marcadores de etapa, si la etapa tiene criterio, si es backlog y las referencias del objetivo. El backend deriva todo lo demás con una sola regla (`ProgressCalculator`, spec `progress-model`). Para cambiar la regla se cambia el backend, no el generador.

## Esquema

```json
{
  "format_version": 2,
  "generator_version": "portfolio.py 2.1",
  "generated_at": "2026-10-15T07:00:00-05:00",
  "projects": {
    "distriapp": {
      "name": "DistriApp — SCI 360",
      "repo": "distriapp",
      "alias": "SCI 360 · Multimarcasa",
      "summary": "Sistema comercial multitenant para distribuidoras.",
      "state": "ACTIVE",
      "priority_rank": 1,
      "stack": ["Java 21", "Spring Boot", "Angular 22"],
      "blockers": [
        {
          "id": "datos-reales",
          "type": "EXTERNAL_DEPENDENCY",
          "description": "Falta el Excel real de ventas del cliente",
          "impact": "Sin él no se puede cerrar la Etapa 3",
          "affects": ["Etapa 3"],
          "owner": "Cliente",
          "created": "2026-10-01",
          "resolution": "El cliente entrega el archivo de septiembre"
        }
      ],
      "source_commit": "8413025",
      "source_modified_at": "2026-10-14T18:00:00-05:00",
      "roadmap": {
        "objective": {
          "name": "MVP E2E sin mocks",
          "criteria": "Un asesor ve diagnósticos calculados con datos reales",
          "scope": ["Etapa 1", "Etapa 2", "openspec:mvp-e2e-sin-mocks"],
          "target_date": "2026-11-30"
        },
        "achieved": [
          { "date": "2026-09-04", "name": "Gobernanza", "criteria": "Documentación consistente con el código" }
        ],
        "stages": [
          {
            "key": "Etapa 1",
            "title": "Etapa 1 — Backend arrancable contra Postgres real",
            "marker": "in_progress",
            "has_criteria": true,
            "backlog": false,
            "tasks": [
              { "text": "Arrancar bootstrap contra Postgres", "line": 42, "checked": true, "cancelled": false },
              { "text": "Soporte offline", "line": 43, "checked": false, "cancelled": true }
            ]
          },
          {
            "key": null,
            "title": "Mejoras futuras",
            "marker": "none",
            "has_criteria": false,
            "backlog": true,
            "tasks": []
          }
        ]
      },
      "openspec": {
        "specs_count": 0,
        "archived_count": 3,
        "commands": ["apply", "archive", "explore", "propose", "sync", "update"],
        "changes": [
          {
            "name": "mvp-e2e-sin-mocks",
            "metadata": "schema: spec-driven\ncreated: 2026-10-05\n",
            "artifacts": {
              "proposal": { "content": "# Proposal\n...", "truncated": false },
              "tasks": { "content": "# Tasks\n...", "truncated": false },
              "specs": [
                { "path": "specs/ingestion/spec.md", "content": "# Spec Delta\n...", "truncated": false }
              ]
            },
            "tasks": [
              { "number": "1.1", "text": "Leer el Excel real", "section": "1. Ingesta", "line": 5, "checked": true, "cancelled": false },
              { "number": "1.2", "text": "Normalizar columnas", "section": "1. Ingesta", "line": 6, "checked": false, "cancelled": false }
            ]
          },
          { "name": "cambio-ilegible", "error": "tasks.md: permiso denegado" }
        ],
        "archived_in_scope": []
      }
    },
    "contact": { "error": "docs/ROADMAP.md: no se pudo leer (permiso denegado)" }
  },
  "activity": {
    "since": "2026-10-08T07:00:00-05:00",
    "projects": {
      "distriapp": [
        { "sha": "53e5562", "at": "2026-10-14T18:30:00-05:00", "subject": "docs(openspec): propuesta usuarios-desde-subscription-hub" }
      ]
    }
  }
}
```

### Nivel archivo

| Campo | Tipo | Requerido | Notas |
|---|---|---|---|
| `format_version` | int | sí | Debe ser `2`. Cualquier otro valor rechaza el archivo entero. |
| `generator_version` | string | no | Se guarda en el sync run, para auditar. |
| `generated_at` | datetime ISO-8601 con offset | sí | Momento de la lectura de los repos. De aquí sale la antigüedad de los datos (`/api/health/sync`). |
| `projects` | objeto `id -> entrada` | sí | Id estable del proyecto (nombre de la carpeta del repo). |
| `activity` | objeto | no | Ver [Actividad](#actividad). Ausente = el backend conserva los commits que tenía. |

### Entrada de proyecto

Una entrada es **o bien** `{ "error": "<motivo>" }` (el repo no se pudo leer), **o bien** el estado completo:

| Campo | Tipo | Requerido | Notas |
|---|---|---|---|
| `name` | string | sí | Nombre mostrado. |
| `repo` | string | sí | Nombre corto del repositorio. |
| `state` | enum | sí | `ACTIVE \| NEXT \| PAUSED \| BLOCKED \| MAINTENANCE \| COMPLETED \| ARCHIVED`. Editorial: lo declara el usuario y **nunca** se deriva del %. |
| `priority_rank` | int ≥ 1 | no | Orden en el portafolio (1 = primero). |
| `alias`, `summary` | string | no | Editoriales. |
| `stack` | string[] | no | |
| `blockers` | objeto[] | no | Ver [Bloqueos](#bloqueos). |
| `source_commit` | string | no | SHA corto del último commit del repo. |
| `source_modified_at` | datetime ISO-8601 con offset | no | Última modificación conocida del repo (commit o archivos sin commitear). Se muestra como "última actividad". |
| `roadmap` | objeto | no | Presente solo si existe `docs/ROADMAP.md`. |
| `openspec` | objeto | no | Presente solo si existe `openspec/`. |

### `roadmap`

| Campo | Notas |
|---|---|
| `objective` | `{name, criteria, scope[], target_date}`, leído de `## Objetivo actual: <nombre>` (formato en `README.md`). `null` o ausente = sin objetivo declarado. `scope` son las referencias en texto, tal cual: el backend las resuelve. |
| `achieved` | `[{date, name, criteria}]`, leído de `## Objetivos cumplidos`. |
| `stages` | Una por encabezado de etapa, en orden. `key` es `Etapa N` / `Fase N` (o `null` si el encabezado no tiene número). `marker`: `closed` (🟢), `in_progress` (🟡 / ⚠️) o `none`. `has_criteria`: la etapa tiene `**Criterio de cierre**`, `**Objetivo**`, `## Objetivo` o `### Resultado`. `backlog`: el encabezado dice "fuera de alcance", "mejoras futuras", "no roadmapeado", "no priorizadas", "ideas" o "backlog". Las tareas de una etapa backlog no se cuentan. |
| `stages[].tasks` | `{text, line, checked, cancelled}`. `cancelled` = texto tachado `~~…~~`. |

### `openspec`

| Campo | Notas |
|---|---|
| `specs_count`, `archived_count` | Conteos de `openspec/specs/*` y `openspec/changes/archive/*`. |
| `commands` | Comandos `opsx` instalados, según los archivos de `.claude/commands/opsx/`. El dashboard solo sugiere estos. |
| `changes` | Cambios activos (`openspec/changes/*` salvo `archive/`). Cada uno: `name`, `metadata` (`.openspec.yaml` como texto), `artifacts` (solo los que existen: `proposal`, `design`, `tasks`, `specs[]` con `path`) y `tasks` (`number`, `text`, `section`, `line`, `checked`, `cancelled`, una por checkbox, incluidas las anidadas). Un cambio ilegible viaja como `{name, error}`. |
| `archived_in_scope` | Solo los cambios archivados que referencia el alcance del objetivo, con `name` (nombre de la carpeta archivada) y `tasks`. Sin artefactos. |

Cada artefacto lleva `content` y `truncated`. **Límite: 200 KB por artefacto.** Si un artefacto lo supera, se envía truncado con `truncated: true`.

### Bloqueos

`blockers[]`: `{id, type, description, impact?, affects?[], owner?, created?, resolution?}`.

- `type` es uno de `PRODUCT_DECISION | EXTERNAL_DEPENDENCY | TECHNICAL | ENVIRONMENT | SECURITY | UNKNOWN`.
- `affects` admite las mismas referencias que el alcance del objetivo (`Etapa 3`, `openspec:<cambio>`).
- Una decisión que afecta a varias cosas es **un** bloqueo con varios `affects`, no varios bloqueos.

### Tareas bloqueadas por texto

Una tarea abierta (de ROADMAP o de `tasks.md`) cuyo texto contenga `Bloqueada: <motivo>`, `Bloqueada (<lo que sea>): <motivo>` o `(bloqueada: <motivo>)`, sin distinguir mayúsculas, queda `BLOCKED` con ese motivo. El motivo llega hasta el primer `. `, o hasta el `)` si el marcador abrió paréntesis, con un máximo de 200 caracteres. Sin los dos puntos no es marcador ("si queda bloqueado"). Una tarea `[x]` o tachada no cambia. El generador no hace nada especial: el backend lo lee del texto.

### Actividad

`activity`: `{since, projects: {id -> [{sha, at, subject}]}}`. Son los commits sin merges de todas las ramas desde `since` (7 días), sin duplicados, máximo 30 por repo, los más recientes primero. Va **fuera de las entradas** porque la ventana se mueve cada día: dentro de la entrada cambiaría su hash aunque el repo no cambiara.

## Cómo aplica el backend cada entrada

1. **Detección de cambios por contenido.** El backend calcula el SHA-256 del JSON canónico de la entrada (claves ordenadas). Si coincide con el guardado, responde `UNCHANGED` y no toca nada. Si es distinto, responde `UPDATED`. Si el id no existía, responde `CREATED`. Así, cualquier cambio en lo reportado (un checkbox sin commitear, un cambio de rúbrica del generador) se aplica sin trucos de fechas.
2. **Reemplazo completo.** Una entrada `UPDATED`/`CREATED` reemplaza el estado entero del proyecto: etapas, tareas, objetivo, cambios y bloqueos. No hay "ausente conserva": lo que no viene, deja de existir.
3. **Excepción, un cambio ilegible.** Un cambio que llega como `{name, error}` conserva su última versión válida (tareas y artefactos) y se marca con el error y su fecha.
4. **Repo ilegible.** Una entrada `{error}` conserva el último estado del proyecto y guarda el error con su fecha.
5. **Proyectos ausentes.** Un proyecto guardado que no aparece en un snapshot válido se marca con `missing_since`, y no se borra. Si vuelve a aparecer, la marca se limpia.
6. **Tareas cerradas.** Antes de reemplazar un proyecto `UPDATED`, el backend compara sus tareas guardadas con las nuevas. Cada tarea que antes estaba abierta y ahora está `[x]` (no tachada) se registra en `task_closed`, con el `generated_at` del snapshot. La tarea se identifica por origen, etapa (`key` o título) o cambio, y número; sin número, por el texto. Una tarea que aparece ya marcada no cuenta, ni las de un proyecto `CREATED`. Es un historial: el sync nunca lo borra.
7. **Commits.** Por cada proyecto de `activity.projects`, sus commits guardados se reemplazan por los recibidos. Un commit sin `sha`, `at` o `subject` se descarta con aviso en el sync run. `activity` no participa en el hash de ninguna entrada.

## Validación

- **Archivo**: JSON inválido, raíz que no es objeto, falta de `projects`, `generated_at` no parseable o `format_version` ≠ 2 → no se aplica nada; el sync run registra el error de archivo.
- **Entrada**: falta `name`/`repo`/`state`, `state` fuera del enum, `priority_rank` no entero o menor que 1, `roadmap`/`openspec` con estructura incorrecta (p. ej. `marker` inválido, `checked` no booleano) → se rechaza esa entrada con su motivo; las demás se aplican.
- **Parcial**: un bloqueo sin `id`/`type`/`description` o con `type` fuera del enum se descarta solo, con aviso en el sync run; el resto de la entrada se aplica.

## Formato obsoleto

Si aparece `progreso.json` o `nuevo.json` en el inbox (un generador anterior a v2), el backend no lo aplica, registra un sync run con el error "formato obsoleto" y borra el archivo. Los datos guardados no cambian.

## Regla de negocio: nunca inventar progreso

Un `[x]` en `docs/ROADMAP.md` o en un `tasks.md` de OpenSpec se marca **solo con verificación real**, nunca porque se escribió el código. El backend confía en esa convención del repo y muestra las tareas como "completadas según el repo". La evidencia independiente (Fase 2) se mostrará aparte.

Una etapa sin marcador de estado y sin criterio de cierre deja sus tareas pendientes en `BLOCKED` por "falta de definición": si no se sabe con qué evidencia se verifica una tarea, eso es un bloqueo real. La solución es escribir el criterio en el roadmap, no tocar el generador.
