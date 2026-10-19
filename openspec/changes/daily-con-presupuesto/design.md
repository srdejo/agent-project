# Design

## Context

Claude Code escribe en `~/.claude/projects/<dir>/<sesión>.jsonl` una línea por evento. Las respuestas del modelo traen `message.id`, `message.usage` (`input_tokens`, `cache_creation_input_tokens`, `cache_read_input_tokens`, `output_tokens`), `timestamp` y `cwd`. Medición del 2026-10-19: 10 archivos en 7 días, ~1.000–1.900 respuestas y 1,4–3,8 M tokens de salida por día. El `cwd` mapea con claridad a los repos (`…/srdejo/beauty`, `…/srdejo/agent-project/backend`).

El cupo del plan no se puede leer localmente: solo `/usage` lo muestra, como un %.

## Goals / Non-Goals

**Goals:**
- Un orden de magnitud honesto de cuánto trabajo cabe hoy.
- Que el daily lo use sin volverse opaco.

**Non-Goals:**
- Exactitud.
- Leer el cupo real del plan.
- Ejecutar nada en la PC (decisión 2026-10-19).
- Contar el uso de claude.ai o de otras máquinas.

## Decisions

### D1. La medición vive en el generador
Lo que se lee está en la PC, y el generador ya corre allí, en la tarea programada de las 07:45 y al desplegar. Sube solo conteos por día y repo, nunca texto.
- *Alternativa descartada*: que el daemon de OpenClaw lo envíe por el WebSocket. Depende de la Etapa 7, todavía inactiva.

### D2. Métrica `input + cache_creation + output`
Las lecturas de caché son la mayor parte del volumen (cientos de millones por día) y el plan las pondera mucho menos. Incluirlas haría el número inútil. Como el cupo semanal se infiere con la misma métrica (D3), un error de ponderación se compensa en buena parte.

### D3. Cupo inferido = uso de 7 días / fracción usada
Daniel lee "X% usado" en `/usage`. Con eso, cupo ≈ tokens de 7 días / X. Es una aproximación por tres motivos:
- la ventana semanal del plan no empieza necesariamente hace 7 días;
- el uso desde otras máquinas o desde claude.ai cuenta en el plan, pero no aquí, así que el cupo sale **subestimado**;
- el plan pondera los tokens a su manera.

Se muestra siempre como "estimado". Si Daniel ve que falla mucho, se ajusta con datos.

### D4. Costo por tarea agregado, no por proyecto
Tokens de 28 días / tareas completadas en 28 días, de todo el portafolio. Por proyecto, la muestra es demasiado chica: 5 proyectos tienen menos de 5 tareas cerradas en el período.

### D5. El % viaja en un `GET` y se guarda en el navegador
Mantiene la regla de no tener endpoints de escritura (decisión del Control Center v2). El `localStorage` es una comodidad por navegador: si se pierde, el daily simplemente sale sin recorte.

### D6. Recorte mínimo
Solo se quitan alternativas; el foco nunca se cambia por uno "más barato". El costo por tarea es un promedio y no sabe cuánto cuesta una tarea concreta.

## Risks / Trade-offs

- [Los datos son estimados y pueden inducir a error] → la vista y el texto dicen "estimado"; con poco historial, `null` sin recorte.
- [El formato de los `.jsonl` de Claude Code no es un contrato público y puede cambiar] → el generador omite las líneas que no entiende y reporta cuántas omitió. Si no puede leer nada, no envía `usage` y el daily sale sin presupuesto.
- [Privacidad: los transcripts contienen todo el trabajo] → solo se envían conteos; un test lo verifica sobre un `.jsonl` de prueba con texto sensible.
