# Proposal

## Why

"Mi trabajo" dice qué se puede hacer, pero no responde lo que Daniel necesita al sentarse a trabajar: *hoy trabajo en esto; para eso necesito esto otro; ejecuto estos comandos; y así retomo*. Además quedan restos de antes de la v2: la página del agente está en inglés y con otro estilo, y `docs/PROGRESS.md` todavía describe el protocolo y la API anteriores como si fueran el estado actual.

## What Changes

- **Daily ("Hoy")** al inicio de Mi trabajo, armado por el backend con reglas fijas a partir de los mismos datos que Mi trabajo. Es determinista: con los mismos datos y el mismo día, sale el mismo daily. Incluye:
  - **Para hoy trabajaré en**: un foco y dos alternativas de otros proyectos, por si el foco está bloqueado.
  - **Para trabajar necesito**: los bloqueos y decisiones de esos proyectos.
  - **Ejecuta en consola**: los comandos para empezar con el foco.
  - **Para continuar**: la instrucción de continuación del foco.
  
  Lleva un botón **Copiar daily** con todo en texto plano, y muestra el aviso de datos obsoletos si corresponde.
- Nuevo endpoint de solo lectura `GET /api/daily`.
- **Página del agente** reescrita en español y con el estilo de la v2: estado, actividad, último heartbeat y eventos. Cuando el daemon no está conectado, explica qué falta.
- **`docs/PROGRESS.md`** reescrito al estado actual; lo anterior queda resumido como historial.

No cambia: el sync, el modelo de datos ni los demás endpoints. Sigue sin haber escrituras ni ejecución desde el dashboard: los comandos se muestran para copiarlos.

## Capabilities

### New Capabilities
- `daily-plan`: el daily del día, cómo elige el foco, las alternativas, las necesidades, los comandos y la continuación.
- `agent-monitor-view`: qué muestra la página del agente y cómo se comporta sin conexión.

### Modified Capabilities
(ninguna: las specs de `control-center-fase-1` siguen sin archivar)

## Impact

- **Backend**:
  - `DailyUseCase`, que se apoya en `WorkUseCase` y `ContinuationUseCase`;
  - `IDailyServicePort`, DTO y mapper; el endpoint va en `WorkRestController`, junto a Mi trabajo.
- **Frontend**:
  - bloque "Hoy" en `features/work`;
  - `features/agent` reescrito.
- **Docs**: `PROGRESS.md` y `ARCHITECTURE.md` (endpoint nuevo).
