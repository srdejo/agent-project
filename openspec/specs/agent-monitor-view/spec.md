# agent-monitor-view Specification

## Purpose
Muestra en español, y con el estilo de la v2, el estado del agente local (OpenClaw), su actividad y sus eventos, y explica qué falta cuando no está conectado.

## Requirements

### Requirement: Página del agente en español y con el estilo de la v2
La ruta `/agent` SHALL mostrar el estado del agente ("En línea", "Pensando", "Ejecutando herramienta", "Desconectado"…), la actividad actual, la hora del último heartbeat y los eventos recibidos, todo en español y con los componentes visuales del resto del dashboard. La página MUST NOT contener textos en inglés.

#### Scenario: Agente conectado
- **WHEN** el daemon envía `agent_status` con `thinking` y un mensaje
- **THEN** la página muestra "Pensando", el mensaje como actividad y la hora del último heartbeat

### Requirement: Sin conexión, explicar qué falta
Si el agente está desconectado, la página SHALL decirlo. SHALL distinguir si lo que falla es la conexión del navegador con el backend o la del daemon con el backend, y SHALL indicar dónde está la guía para conectarlo (`docs/AGENT_MONITOR.md`). MUST NOT mostrar eventos simulados.

#### Scenario: Daemon apagado
- **WHEN** el navegador está conectado al WebSocket pero el agente está `offline`
- **THEN** la página muestra "Desconectado", explica que el daemon local no está enviando heartbeats y remite a `docs/AGENT_MONITOR.md`
