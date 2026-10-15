# Spec Delta

## Purpose

Indica cuándo se sincronizaron por última vez los datos, si la última corrida falló, cuánto tiempo tienen los datos que se muestran y si el agente está conectado, cada señal por separado.

## ADDED Requirements

### Requirement: Registro de cada sync run
Cada procesamiento del inbox SHALL registrar un sync run con:
- la fecha de inicio y la duración en ms;
- `format_version`, `generator_version` y `generated_at` del snapshot, si se pudieron leer;
- el error de archivo, si lo hubo;
- los ids creados, actualizados, sin cambios, rechazados (con motivo), con error de repo y ausentes;
- los avisos parciales, como bloqueos descartados o referencias no resueltas.

#### Scenario: Snapshot ilegible
- **WHEN** `snapshot.json` no es JSON válido
- **THEN** el run queda registrado con el error de archivo y su duración, y ningún proyecto cambia

### Requirement: Endpoint de salud del sync
El sistema SHALL exponer `GET /api/health/sync` con estos campos:
- `lastAttemptedSync`: el último run;
- `lastSuccessfulSync`: el último run sin error de archivo y con al menos un proyecto `CREATED`, `UPDATED` o `UNCHANGED`;
- `lastRun`: el resumen del último run;
- `dataAgeSeconds`: los segundos transcurridos desde `generated_at` del último run correcto;
- `stale`: `true` si `dataAgeSeconds` supera el umbral;
- `agentConnectivity`: `online` u `offline`.

Si no existe ningún run correcto, las fechas SHALL ser `null` y `stale` SHALL ser `true`.

#### Scenario: Última corrida fallida
- **WHEN** el último run tuvo un error de archivo y el anterior, correcto, se generó hace 30 horas
- **THEN** las fechas `lastAttemptedSync` y `lastSuccessfulSync` son distintas, y `stale = true` con el umbral por defecto

#### Scenario: Sin runs
- **WHEN** la base está recién creada
- **THEN** `lastSuccessfulSync = null` y `stale = true`

### Requirement: Conectividad del agente independiente de la API
`agentConnectivity` SHALL derivarse solo del heartbeat del cliente agente en el WebSocket `/ws/agent`. Que la API responda MUST NOT implicar que el agente está conectado.

#### Scenario: Daemon apagado
- **WHEN** el backend responde, pero no hay heartbeat del agente desde hace 45 s
- **THEN** `agentConnectivity = offline`

### Requirement: Umbral configurable
El umbral de `stale` SHALL configurarse por propiedad. Por defecto vale 26 horas: el generador programado corre una vez al día y el margen cubre una corrida tardía.

#### Scenario: Valor por defecto
- **WHEN** no se configura el umbral y los datos se generaron hace 25 horas
- **THEN** `stale = false`

### Requirement: Modo de datos obsoletos
Todas las vistas SHALL mostrar en la cabecera la antigüedad de los datos y el estado del agente. Si `stale = true`, SHALL mostrar un aviso visible y MUST NOT presentar ningún dato como "en vivo".

#### Scenario: Datos de hace dos días
- **WHEN** `dataAgeSeconds` equivale a 48 horas
- **THEN** la cabecera muestra "Datos de hace 2 días" como aviso
