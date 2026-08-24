# OpenClaw Agent Monitor

The dashboard and backend share one native WebSocket endpoint: `wss://agent.srdejo.com.co/ws/agent`.

## Message protocol

The local daemon sends JSON text messages. Heartbeats are emitted every 30 seconds and the server marks the agent offline after 45 seconds without an inbound message.

```json
{"type":"heartbeat","timestamp":"2026-08-23T12:00:00.000Z"}
{"type":"agent_status","status":"thinking","message":"Pensando respuesta...","timestamp":"2026-08-23T12:00:01.000Z"}
{"type":"log_event","level":"INFO","message":"Mensaje recibido","timestamp":"2026-08-23T12:00:02.000Z"}
```

Allowed statuses are `idle`, `receiving_message`, `thinking`, `executing_tool`, `replying`, and `error`. Allowed log levels are `INFO`, `WARN`, and `ERROR`.

## VPS setup

1. Generate a long random token and set `AGENT_WS_TOKEN` in the backend `.env`.
2. Add the `/ws/` nginx location shown in `docs/DEPLOYMENT.md`, then run `nginx -t` and reload nginx.
3. Redeploy the backend and frontend with `infra/deploy.ps1`.

The browser connects without the token and is read-only. Only a client declaring `role=agent` must provide the token.

## Local setup

```powershell
Set-Location ..\infra\openclaw-daemon
npm install
$env:AGENT_WS_TOKEN = 'the-same-token-configured-on-the-vps'
npm start
```

Import `setAgentStatus` and `logEvent` into the OpenClaw wrapper and call them around message reception, reasoning, tool execution, response delivery, and errors. These calls only send when the connection is open; they do not block OpenClaw while reconnecting.