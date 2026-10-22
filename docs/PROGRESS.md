# PROGRESS.md

Estado del proyecto a **2026-10-22**. Actualizar este archivo cada vez que se cierre una tarea del ROADMAP.

## Estado actual

**Control Center v2 en producción** (`https://agent.srdejo.com.co`, detrás de oauth2-proxy con login de Google). Daniel lo confirmó en el navegador el 2026-10-19. Etapa 8 cerrada y cambio `control-center-fase-1` completo (27/27). Lo que está en vivo:
- **Backend**: arquetipo `powerup-arquetipo-rx` (Java 26, Spring Boot 4.1, WebFlux, R2DBC) corriendo con el JDK 26 estándar del VPS (`/home/srdejo/opt/jdk-26`, por el drop-in `java26.conf`). Esquema V8. Los datos entran solo por el `snapshot.json` del inbox (sync v2, `docs/SYNC_PROTOCOL.md`).
- **API de solo lectura**: `/api/portfolio`, `/api/projects/**`, `/api/work`, `/api/health/sync` y `/api/daily` (ver `docs/ARCHITECTURE.md`).
- **Frontend**: Mi trabajo, portafolio, detalle de proyecto, detalle de cambio y la página del agente.
- **Generador**: `infra/portfolio/portfolio.py` 2.1 (con el bloque `activity` de commits), con tarea programada diaria a las 07:45 en la PC de Daniel. Mide el avance contra el `## Objetivo actual` del `docs/ROADMAP.md` de cada repo; los 11 repos lo declaran.

## Tarea actual

Cambio OpenSpec `daily-hecho-y-bloqueos`, **desplegado el 2026-10-22**. El daily responde tres preguntas:
- **Hecho**: commits y tareas cerradas por proyecto desde las 00:00 del último día anterior con actividad. Los commits llegan en el bloque `activity` del snapshot (tabla `repo_commit`) y las tareas cerradas las registra el sync al comparar contra lo guardado (`task_closed`).
- **Para hoy trabajaré en**: foco y alternativas de Mi trabajo, sin las tareas que el repo declara `Bloqueada: <motivo>` (`ProgressCalculator`, `RULE_VERSION` 2).
- **Bloqueos**: los editoriales de todos los proyectos `ACTIVE`/`NEXT`/`BLOCKED` y las tareas bloqueadas por texto. Reemplaza a `needs`.

Evidencia: 106 tests del backend y `./gradlew build` en verde; 3 tests del frontend. En local, con el snapshot real y en Chrome headless a 375 y 1280 px, se ven las tres secciones, no hay overflow y "Copiar daily" copia el `text`. En producción, por loopback (`curl 127.0.0.1:8083/api/daily`), tras el deploy y el sync (12 actualizados): `done` con 5 proyectos desde el 2026-10-21, foco `distriapp` 4.10 (la 4.9 está bloqueada) y 7 bloqueos, incluida la 4.9 con su motivo. Las tareas cerradas se registran desde ese sync.

Los comandos `daily` y `srdejo` funcionan también por SSH (cmd): `infra/bin/` en el PATH de usuario, con la lógica en `infra/portfolio/daily.ps1`.

## Bloqueadores

Ninguno.

## Próximo paso

1. Que Daniel confirme el daily en el navegador y archivar `daily-hecho-y-bloqueos`.
2. Etapa 7, que sigue abierta: la ruta `/ws/` en nginx y `AGENT_WS_TOKEN` en producción, y arrancar `infra/openclaw-daemon` junto a OpenClaw (`docs/AGENT_MONITOR.md`). Hasta entonces la página del agente muestra "Desconectado".
3. En espera, por decisión de Daniel: la migración de los backends al arquetipo (cambio paraguas `migrar-portafolio-arquetipo-rx` en el workspace), con `hotel` como piloto, cuando agent-project esté estable en producción.

## Historial (resumen)

Lo anterior a la v2 ya no está en el código: se reemplazó en la reestructuración de octubre. Detalle en `docs/DECISIONS.md` y en el historial de git.
- **2026-08**: primera versión. Backend multi-módulo con Spring MVC y JPA, protocolo `progreso.json`/`nuevo.json`, `GET /api/projects` y dashboard mobile-first. Desplegada en `nolost-vps` con dominio y certificado propios. Inbox con `WatchService`, sin polling. Logging HTTP con `requestId`. Monitor del agente por WebSocket (Etapa 7) escrito, pero sin activar en producción.
- **2026-10-15 a 19**: Control Center v2 (cambio `control-center-fase-1`). Reestructuración completa sobre el arquetipo reactivo, sync v2 con un solo `snapshot.json` por hash de contenido, avance contra el objetivo de cada repo, Mi trabajo y continuación de cambios. La V7 borró las tablas anteriores, con un respaldo previo guardado fuera del repo (`backups/` está en `.gitignore`).
- **2026-10-22**: daily con Hecho, Para hoy y Bloqueos (cambios `daily-y-limpieza`, archivado, y `daily-hecho-y-bloqueos`).

## Última actualización

2026-10-22
