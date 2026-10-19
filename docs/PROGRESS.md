# PROGRESS.md

Estado del proyecto a **2026-10-19**. Actualizar este archivo cada vez que se cierre una tarea del ROADMAP.

## Estado actual

**Control Center v2 en producción** (`https://agent.srdejo.com.co`, detrás de oauth2-proxy con login de Google). Daniel lo confirmó en el navegador el 2026-10-19. Etapa 8 cerrada y cambio `control-center-fase-1` completo (27/27). Lo que está en vivo:
- **Backend**: arquetipo `powerup-arquetipo-rx` (Java 26, Spring Boot 4.1, WebFlux, R2DBC) corriendo con el JDK 26 estándar del VPS (`/home/srdejo/opt/jdk-26`, por el drop-in `java26.conf`). Esquema V7. Los datos entran solo por el `snapshot.json` del inbox (sync v2, `docs/SYNC_PROTOCOL.md`).
- **API de solo lectura**: `/api/portfolio`, `/api/projects/**`, `/api/work`, `/api/health/sync` y, con el cambio `daily-y-limpieza`, `/api/daily` (ver `docs/ARCHITECTURE.md`).
- **Frontend**: Mi trabajo, portafolio, detalle de proyecto, detalle de cambio y la página del agente.
- **Generador**: `infra/portfolio/portfolio.py` v2, con tarea programada diaria a las 07:45 en la PC de Daniel. Mide el avance contra el `## Objetivo actual` del `docs/ROADMAP.md` de cada repo; los 11 repos lo declaran.

## Tarea actual

Cambio OpenSpec `daily-y-limpieza`:
- **Hecho y verificado en local**: el daily "Hoy" en Mi trabajo (`GET /api/daily`, 86 tests en verde; en Chrome headless con datos reales a 375 y 1280 px, las cuatro secciones aparecen, "Copiar daily" copia exactamente el `text` y no hay overflow); la página `/agent` en español con el estilo de la v2; este archivo, reescrito.
- **Falta**: desplegar con la confirmación de Daniel (tarea 3.2).

## Bloqueadores

Ninguno.

## Próximo paso

1. Desplegar `daily-y-limpieza` y que Daniel vea el daily en producción.
2. Etapa 7, que sigue abierta: la ruta `/ws/` en nginx y `AGENT_WS_TOKEN` en producción, y arrancar `infra/openclaw-daemon` junto a OpenClaw (`docs/AGENT_MONITOR.md`). Hasta entonces la página del agente muestra "Desconectado".
3. En espera, por decisión de Daniel: la migración de los backends al arquetipo (cambio paraguas `migrar-portafolio-arquetipo-rx` en el workspace), con `hotel` como piloto, cuando agent-project esté estable en producción.

## Historial (resumen)

Lo anterior a la v2 ya no está en el código: se reemplazó en la reestructuración de octubre. Detalle en `docs/DECISIONS.md` y en el historial de git.
- **2026-08**: primera versión. Backend multi-módulo con Spring MVC y JPA, protocolo `progreso.json`/`nuevo.json`, `GET /api/projects` y dashboard mobile-first. Desplegada en `nolost-vps` con dominio y certificado propios. Inbox con `WatchService`, sin polling. Logging HTTP con `requestId`. Monitor del agente por WebSocket (Etapa 7) escrito, pero sin activar en producción.
- **2026-10-15 a 19**: Control Center v2 (cambio `control-center-fase-1`). Reestructuración completa sobre el arquetipo reactivo, sync v2 con un solo `snapshot.json` por hash de contenido, avance contra el objetivo de cada repo, Mi trabajo y continuación de cambios. La V7 borró las tablas anteriores, con un respaldo previo guardado fuera del repo (`backups/` está en `.gitignore`).

## Última actualización

2026-10-19
