# Spec Delta

## Purpose

Define qué significa "avance" en el Project Control Center. Cada proyecto mide su progreso contra un objetivo declarado en su propio ROADMAP, que combina etapas del ROADMAP y cambios OpenSpec. El estado de cada tarea se deriva con una única regla verificable.

## ADDED Requirements

### Requirement: Sección de objetivo en el ROADMAP
El estándar Agent Project SHALL definir esta sección de nivel 2 en `docs/ROADMAP.md`:

```
## Objetivo actual: <nombre>

**Criterio de cierre**: <qué tiene que ser cierto para darlo por cumplido>
**Alcance**: <referencia>, <referencia>, ...
**Fecha objetivo**: <AAAA-MM-DD>        (opcional)
```

Una referencia es la clave de una etapa del mismo ROADMAP (`Etapa 5`, `Fase 2`) o un cambio OpenSpec (`openspec:<nombre>`). Opcionalmente, el ROADMAP SHALL poder incluir `## Objetivos cumplidos`, con una línea por objetivo: `- AAAA-MM-DD — <nombre>: <criterio>`.

Ninguna de las dos secciones SHALL contarse como etapa, y sus checkboxes, si los hubiera, MUST NOT contarse como tareas.

#### Scenario: Objetivo declarado
- **WHEN** el ROADMAP contiene `## Objetivo actual: MVP E2E sin mocks` con `**Alcance**: Etapa 1, Etapa 2, openspec:mvp-e2e-sin-mocks`
- **THEN** el proyecto tiene un objetivo actual con ese nombre y tres referencias de alcance

#### Scenario: Sección con criterio de etapa
- **WHEN** una etapa contiene una línea `**Objetivo**: ...` (criterio de etapa) y no hay sección `## Objetivo actual`
- **THEN** esa línea sigue contando como criterio de la etapa y el proyecto queda "Sin objetivo declarado"

### Requirement: Resolución del alcance
Cada referencia del alcance SHALL resolverse contra:
- una etapa del ROADMAP, comparando la clave del inicio del título sin distinguir mayúsculas;
- un cambio OpenSpec activo o archivado del mismo repo.

Una referencia que no resuelve SHALL marcarse como `unresolved`, mostrarse como aviso en el objetivo y no aportar tareas. Una tarea que entra por dos referencias SHALL contarse una sola vez.

#### Scenario: Referencia inexistente
- **WHEN** el alcance incluye `Etapa 9` y el ROADMAP no tiene esa etapa
- **THEN** el objetivo muestra "Referencia no encontrada: Etapa 9", y el avance se calcula con el resto

#### Scenario: Cambio archivado en el alcance
- **WHEN** el alcance incluye `openspec:x` y `x` ya está archivado con 10 tareas marcadas
- **THEN** sus 10 tareas cuentan como completadas en el objetivo

### Requirement: Estados de tarea
El backend SHALL derivar el estado de cada tarea así, en este orden:
1. Texto tachado → `CANCELLED`.
2. `checked` → `DONE`.
3. Tarea del ROADMAP sin marcar, en una etapa sin marcador de estado y sin criterio de cierre → `BLOCKED`, con motivo "falta de definición".
4. Tarea del ROADMAP sin marcar, en una etapa marcada en curso → `IN_PROGRESS`.
5. Cualquier otra tarea sin marcar → `OPEN`.

Las tareas de las etapas de backlog (fuera de alcance, mejoras futuras, ideas) MUST NOT derivarse ni contarse.

#### Scenario: Etapa sin definición
- **WHEN** una etapa no tiene marcador ni criterio de cierre y tiene `- [ ] Integrar pagos`
- **THEN** la tarea queda `BLOCKED` con motivo "falta de definición"

#### Scenario: Tarea tachada
- **WHEN** una línea es `- [ ] ~~Soporte offline~~`
- **THEN** la tarea queda `CANCELLED`

### Requirement: Completada según el repo, separada de la evidencia
Un `[x]` en el ROADMAP y un `[x]` en el `tasks.md` de un cambio OpenSpec SHALL contar igual como `DONE`, porque ambos repos los marcan solo tras verificar. El dashboard SHALL etiquetar el conteo como "completadas según el repo" y mostrar aparte el indicador "evidencia independiente: no disponible". Ese indicador cambiará cuando exista registro de evidencia (Fase 2).

#### Scenario: Etiquetado
- **WHEN** un objetivo tiene 21 tareas `DONE` de 36
- **THEN** se muestra "21/36 completadas según el repo" y "evidencia independiente: no disponible"

### Requirement: Avance del objetivo
El avance principal del proyecto SHALL ser `round(100 × DONE / (total − CANCELLED))`, calculado sobre las tareas del alcance resuelto del objetivo actual. Siempre SHALL mostrarse junto a su numerador y su denominador. Si el denominador es 0, SHALL mostrarse "Sin datos" en lugar de un porcentaje. Si el proyecto no tiene objetivo actual, SHALL mostrarse "Sin objetivo declarado" en lugar del avance principal.

#### Scenario: Objetivo mixto
- **WHEN** el alcance resuelve 20 tareas del ROADMAP (12 `DONE`, 1 `CANCELLED`) y 17 de OpenSpec (9 `DONE`)
- **THEN** el avance es `round(100 × 21 / 36) = 58%`, mostrado como `58% (21/36)`

#### Scenario: Sin objetivo
- **WHEN** el ROADMAP no tiene `## Objetivo actual`
- **THEN** el avance principal se muestra como "Sin objetivo declarado" y el avance del ROADMAP aparece como dato secundario

### Requirement: Avance del ROADMAP como dato secundario
El sistema SHALL calcular también el avance de todo el ROADMAP con la misma fórmula, sobre todas las tareas fuera de backlog. Este avance SHALL mostrarse como secundario y MUST NOT usarse como avance principal.

#### Scenario: Ambos avances
- **WHEN** el objetivo va en 58% y el ROADMAP completo en 65%
- **THEN** la tarjeta muestra 58% como principal y "Roadmap 65% (42/65)" como secundario

### Requirement: Objetivo cumplido
Un objetivo cuyas tareas del alcance son todas `DONE` o `CANCELLED`, y con al menos una `DONE`, SHALL mostrarse como "Objetivo cumplido". El dashboard SHALL pedir que se declare el siguiente objetivo y MUST NOT presentar el proyecto como terminado por ese motivo. El estado del proyecto lo declara la capa editorial.

#### Scenario: Objetivo al 100%
- **WHEN** todas las tareas del alcance de `micasachurch` están `DONE`
- **THEN** el proyecto muestra "Objetivo cumplido · declara el siguiente" y aparece en "Revisión requerida" de Mi trabajo

### Requirement: Historial de avance por objetivo
En cada actualización de un proyecto, el sistema SHALL guardar un punto de historial con el nombre del objetivo, DONE, el denominador y la fecha. La gráfica del detalle SHALL mostrar solo los puntos del objetivo actual. Los objetivos cumplidos SHALL listarse a partir de `## Objetivos cumplidos`.

#### Scenario: Cambio de objetivo
- **WHEN** el objetivo pasa de "MVP" a "Piloto con cliente"
- **THEN** la gráfica empieza vacía para "Piloto con cliente", y "MVP" aparece en la lista de objetivos cumplidos si el ROADMAP lo declara ahí
