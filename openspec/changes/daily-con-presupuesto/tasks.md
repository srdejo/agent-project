# Tasks

## 1. Generador y contrato

- [ ] 1.1 `docs/SYNC_PROTOCOL.md`: sección opcional `usage` (`days`: fecha, `total`, `byRepo`, `skippedLines`), compatible con `format_version` 2. Verificar: `SnapshotParserTest` parsea el ejemplo del documento.
- [ ] 1.2 `portfolio.py` lee `~/.claude/projects/*.jsonl` (28 días, America/Bogota, deduplicado por `message.id`, repo según el `cwd`) y agrega `usage`. Verificar con tests sobre `.jsonl` de prueba: duplicados, subdirectorios, `cwd` fuera del workspace (va a "otros"), líneas ilegibles (se cuentan en `skippedLines`) y ningún texto de la conversación en la salida.

## 2. Backend

- [ ] 2.1 `V8__claude_usage_day.sql`, modelo, puerto y adaptador R2DBC. El sync reemplaza la serie completa en la misma transacción. Verificar: test de integración contra Postgres local.
- [ ] 2.2 Cálculo del costo por tarea y del presupuesto del día (spec `usage-budget`) en el dominio, con `Clock`. Verificar con tests unitarios: menos de 5 tareas da `null`, fracción usada 0, días hasta el lunes y redondeo hacia abajo.
- [ ] 2.3 `GET /api/daily?availablePercent=N` con `budget` en la respuesta y el recorte de alternativas. Sin el parámetro, la respuesta es igual a la anterior. Verificar: tests unitarios del recorte y test HTTP con y sin el parámetro, incluido un valor inválido, que debe dar 400.

## 3. Frontend

- [ ] 3.1 Campo "% disponible según /usage", guardado en `localStorage` y con validación 0–100; presupuesto, costo por tarea y uso de 7 días visibles como estimados. Verificar en Chrome headless: el valor persiste al recargar, el recorte se ve con 10% y no hay overflow a 375 px.

## 4. Despliegue

- [ ] 4.1 Respaldo, despliegue y sync con `usage` real (con la confirmación de Daniel). Verificar: `/api/daily?availablePercent=…` en producción por loopback, y Daniel compara el presupuesto con lo que ve en `/usage`.
