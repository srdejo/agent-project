# OpenClaw Project Control Center

## 1. Propósito

**OpenClaw Project Control Center** es una plataforma pública para visualizar y seguir el avance de múltiples proyectos de software administrados parcialmente por agentes de IA como OpenClaw.

La plataforma no pretende reemplazar `ROADMAP.md`, `PROGRESS.md` ni Git. Su objetivo es convertir el estado técnico de los proyectos en una vista gráfica, histórica y consultable.

### Objetivos

- Visualizar el avance de múltiples proyectos en un solo lugar.
- Mostrar etapas, tareas, bloqueos y próximos pasos.
- Registrar el historial de cambios de avance.
- Mostrar actividad realizada por agentes de IA.
- Detectar inconsistencias entre el roadmap, el progreso y el estado real del repositorio.
- Permitir consultar el estado desde una interfaz web pública.
- Permitir que OpenClaw actualice automáticamente el tracking.
- Mantener los repositorios y documentos del proyecto como fuente de verdad.
- Evitar que información privada, secretos o credenciales sea publicada.

---

## 2. Principio fundamental

Diferenciar entre: 1) fuente de verdad del proyecto (repo: ROADMAP.md, PROGRESS.md, DECISIONS.md, ARCHITECTURE.md, código, Git, tests, CI), 2) tracking histórico (base de datos), 3) visualización (dashboard). El dashboard nunca sustituye esos archivos; el proyecto debe seguir funcionando aunque el dashboard no esté disponible.

## 3. ¿Es necesaria una base de datos?

Sí, si el dashboard debe ser público y conservar historial (snapshots, cambios, eventos del agente, ejecuciones, verificaciones, commits, bloqueos, métricas históricas). Para leer solo el estado actual de Markdown no sería estrictamente necesaria.

## 4. Arquitectura propuesta

```text
Git Repo (ROADMAP.md, PROGRESS.md, DECISIONS.md, Code)
        │
   OpenClaw Agent
        │
  ┌─────┴─────┐
  ▼           ▼
Project    Task
Analysis   Execution
  └─────┬─────┘
        ▼
  Tracking API
        ▼
   PostgreSQL (Projects, Tasks, Events, Snapshots, Agent Runs, Verifications)
        ▼
  Public Dashboard
```

## 5. Componentes

- **Repositorios**: cada proyecto mantiene sus propios archivos (`CLAUDE.md`, `ROADMAP.md`, `PROGRESS.md`, `docs/`, `backend/`, `frontend/`, `.git/`). No se obliga a cambiar su estructura.
- **Project Control API**: registra proyectos, recibe eventos y actualizaciones de progreso, almacena snapshots, consulta tareas/actividad, calcula métricas, expone info pública. Sugerido: Spring Boot + PostgreSQL + REST.
- **PostgreSQL** — modelo inicial: `projects`, `project_stages`, `project_tasks`, `project_snapshots`, `agent_runs`, `agent_events`, `verification_runs`, `git_commits`, `blockers`. MVP: `projects`, `project_tasks`, `project_snapshots`, `agent_events`.

## 6. Modelo de tarea

```json
{ "project": "hotel-management", "task": "housekeeping", "stage": 8, "status": "completed", "verified": true, "source": "ROADMAP.md" }
```

Estados: `TODO`, `IN_PROGRESS`, `BLOCKED`, `COMPLETED`, `CANCELLED`. `verified` es clave — una tarea no se considera completada solo porque el agente diga que terminó.

## 7. Verificación

El proyecto puede definir comandos de verificación (`./gradlew test`, `npm run test`, `npm run build`). OpenClaw los ejecuta antes de reportar una tarea como completada.

## 8. Tarea automática `project-progress-sync`

Job periódico (inicialmente cada 30 min, luego post-commit/post-test/cron/manual) que lee `ROADMAP.md`/`PROGRESS.md`, inspecciona commits recientes, detecta cambios e inconsistencias, verifica evidencia antes de marcar completado, reporta bloqueadores, actualiza `PROGRESS.md` y envía un evento estructurado a la API. Nunca expone secretos ni inventa progreso; si el estado es ambiguo, lo reporta como "requiere atención".

## 9. Regla crítica: no inventar progreso

> **No se actualiza el porcentaje porque el agente escribió código. Se actualiza cuando existe evidencia de avance** (tarea del roadmap completa + implementación verificada).

## 10. Evento de sincronización

```json
{
  "project": "hotel-management", "type": "PROGRESS_SYNC", "timestamp": "2026-08-18T22:30:00Z",
  "stage": { "number": 8, "name": "Remaining domain modules", "status": "IN_PROGRESS" },
  "tasks": { "completed": 2, "inProgress": 0, "blocked": 0, "pending": 3 },
  "verification": { "status": "PASSED" },
  "nextTask": "shop", "sourceCommit": "abc123"
}
```

## 11. Snapshot

`project_snapshots`: `id`, `project_id`, `timestamp`, `stage`, `completed_tasks`, `pending_tasks`, `blocked_tasks`, `progress_percentage`, `commit_sha`, `verification_status`. Permite graficar evolución de progreso en el tiempo.

## 12. Dashboard público

Dos niveles: **vista global** (lista de proyectos con % y estado) y **vista de proyecto** (progreso, etapa actual, completado/siguiente/bloqueado, última verificación, última actualización).

## 13. Actividad del agente

Feed de eventos (`22:30 ✓ Progress synchronization`, `21:30 🤖 Started shop implementation`) — distingue estado del proyecto, trabajo del agente y verificaciones.

## 14. Seguridad del dashboard público

El dashboard puede ser público aunque los repos no lo sean. Nunca publicar `.env`, API keys, JWT secrets, credenciales, URLs privadas, datos de clientes ni logs con secretos. Solo información apropiada para publicación (ej. "Login multi-tenant ambiguity", nunca `JWT_SECRET=...`).

## 15–17. Repositorios públicos, fuente de verdad, Git como auditoría

Si el repo es público el dashboard puede enlazarlo directamente, pero la API no debe depender de eso. Prioridad de información: código+tests+verificación > Git > ROADMAP.md > PROGRESS.md > tracking DB > dashboard. El dashboard nunca modifica silenciosamente el estado del proyecto — el flujo es OpenClaw → PROGRESS.md → commit → Tracking API → Dashboard. Cada cambio de progreso debe poder relacionarse con un commit.

## 18. Arquitectura de la primera versión (MVP)

```text
OpenClaw → REST → Project Control API → PostgreSQL → Angular Dashboard
```

Backend: Spring Boot + PostgreSQL + REST. Frontend: Angular + Tailwind. Agente: OpenClaw. Deployment: jar + systemd + Nginx + HTTPS.

## 19. Primera versión funcional

Debe permitir: registrar un proyecto y su repositorio, indicar dónde están `ROADMAP.md`/`PROGRESS.md`, sincronizar, almacenar snapshots, mostrar %, etapas, tareas, bloqueos, último commit, actividad del agente e historial de progreso. Fuera de alcance inicial: gestión tipo Jira, comentarios, asignaciones complejas, notificaciones avanzadas, CI/CD propio, gestión de usuarios compleja.

## 20. Evolución futura

Agent runs detallados, métricas (velocidad, tareas/semana, tiempo promedio, % verificado), integraciones (GitHub, GitLab, WhatsApp, Slack, Discord, CI/CD).

## 21. Principio de diseño

El dashboard debe responder rápido: ¿qué proyectos tengo?, ¿en qué % está cada uno?, ¿qué hace el agente ahora?, ¿qué se terminó?, ¿qué está bloqueado?, ¿cuál es el siguiente paso?, ¿cómo ha evolucionado?, ¿qué evidencia existe de que una tarea terminó? Si una funcionalidad no responde estas preguntas, no es prioritaria para el MVP.

## 22. Definición de éxito

Poder abrir el dashboard público y en menos de 30 segundos entender el estado de todos los proyectos sin abrir cada repositorio manualmente.

---

*Este documento y el mockup visual (`OpenClaw Control Center.dc.html`) se importaron desde Claude Design el 2026-08-19. El diseño visual implementado en `frontend/` sigue esta paleta y tipografía; el modelo de datos aquí descrito es la referencia para las Etapas 1–4 de `ROADMAP.md`, más allá de los tres módulos actuales del backend (`projects`, `parser`, `progress`).*
