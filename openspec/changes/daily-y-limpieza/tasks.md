# Tasks

## 1. Backend: daily

- [x] 1.1 Modelo `domain/model/view/Daily`, puerto `IDailyServicePort` y `DailyUseCase` (design D1–D5), conectados en `BeanConfiguration`. Verificar con tests unitarios (puertos en memoria): determinismo, foco de un cambio en el objetivo, alternativas de proyectos distintos, foco del ROADMAP con su instrucción, repo sin `apply`, necesidades del foco primero, fallback a decisiones del portafolio, sin candidatos y aviso `stale`.
- [x] 1.2 `GET /api/daily`: DTO, mapper (MapStruct), handler y controlador con OpenAPI. Verificar con `WebTestClient` sobre datos sembrados, comprobando la forma de la respuesta y que el `text` contiene las cuatro secciones.

## 2. Frontend

- [x] 2.1 Bloque "Hoy" al inicio de Mi trabajo: foco, alternativas, necesidades, comandos y continuación, con enlaces, el botón "Copiar daily" y el aviso `stale`, sin botón de ejecutar. Verificar en Chrome headless contra datos reales: las cuatro secciones aparecen, lo copiado coincide con `text` y no hay overflow a 375 px.
- [x] 2.2 Página `/agent` reescrita en español con el estilo de la v2, con `lastSeen` y la explicación cuando está desconectado. Verificar: un `grep` de los textos en inglés anteriores no encuentra nada, y en headless la página muestra "Desconectado" y la referencia a `docs/AGENT_MONITOR.md`.

## 3. Docs y despliegue

- [x] 3.1 `docs/PROGRESS.md` reescrito al estado actual (historial resumido) y `docs/ARCHITECTURE.md` con `/api/daily`. Verificar: `PROGRESS.md` no describe `progreso.json` ni `/api/projects` como vigentes.
- [ ] 3.2 Desplegar (con la confirmación de Daniel) y verificar en producción `GET /api/daily` por loopback; Daniel lo ve en el navegador.
