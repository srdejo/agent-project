# Design

## Context

`WorkUseCase` ya calcula "Continuar", "Accionables" y "Decisiones" con reglas fijas, y `ContinuationUseCase` genera la instrucción para retomar un cambio. El daily es una **vista** sobre esos datos: no hay datos nuevos ni cambia el sync.

## Goals / Non-Goals

**Goals:** un daily determinista, copiable y enlazado; una página del agente coherente con la v2.
**Non-Goals:** generar el texto con un LLM (descartado por Daniel), ejecutar comandos, guardar dailies anteriores o elegir el foco a mano.

## Decisions

### D1. `DailyUseCase` compone, no recalcula
Pide a `IWorkServicePort` el `Work` y, para el foco OpenSpec, a `IContinuationServicePort` la instrucción. Así el orden del daily es siempre el de Mi trabajo y no aparece una segunda regla de prioridad. Para un foco del ROADMAP arma una instrucción propia y corta (spec `daily-plan`), porque `ContinuationUseCase` es solo para cambios.
- *Alternativa descartada*: leer los puertos de persistencia directamente. Duplicaría la selección de Mi trabajo.

### D2. Candidatos de proyectos distintos
Primero los `continue` y luego el primer `actionable` de cada proyecto que aún no tenga candidato. El foco es el primero, y las alternativas son los siguientes de proyectos que todavía no estén en el daily. Así un proyecto con varios cambios a medias no copa el día.

### D3. Necesidades
Los bloqueos de los proyectos del daily (del `Project` guardado; vienen en `ProjectCard.mainBlocker` y en el detalle). Para no hacer N llamadas, `DailyUseCase` usa el puerto de persistencia (`findProject`) solo para los, como mucho, 3 proyectos elegidos. Si no hay, se usan las dos primeras `decisions` del portafolio.

### D4. Fecha y determinismo
La fecha sale de un `Clock` inyectado, en America/Bogota. El texto no incluye la hora, así que es el mismo durante todo el día mientras no haya un sync nuevo.

### D5. Comandos
Son relativos a la raíz del workspace (`cd <repo>`), porque el backend no conoce rutas locales y no debe conocerlas. `/opsx:apply` solo se sugiere si el repo lo tiene instalado (`openspecCommands`).

### D6. Página del agente
Se reescribe `features/agent` con el shell y las clases de la v2. Se conserva `AgentMonitorService`, que ya funciona. `lastSeen` viene en el `agent_snapshot`; el servicio pasa a exponerlo.

## Risks / Trade-offs

- [El foco del día "rebota" entre proyectos tras cada sync] → Es el comportamiento esperado: refleja el estado. El orden es estable mientras los datos no cambien.
- [Los comandos asumen la consola en la raíz del workspace] → El daily lo dice en una línea antes de los comandos.
