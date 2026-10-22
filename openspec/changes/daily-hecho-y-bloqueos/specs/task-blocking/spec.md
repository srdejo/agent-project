# Spec Delta

## Purpose

Permite declarar en el propio repo, con texto, que una tarea está bloqueada y por qué, para que el dashboard no la
proponga como trabajo y la muestre como bloqueo.

## ADDED Requirements

### Requirement: Marcador de tarea bloqueada
Una tarea sin marcar y no tachada, de ROADMAP o de OpenSpec, SHALL quedar en estado `BLOCKED` cuando su texto contiene,
sin distinguir mayúsculas, una de estas formas:
- `Bloqueada: <motivo>` o `Bloqueado: <motivo>`;
- `Bloqueada (<cualquier texto>): <motivo>`;
- `(bloqueada: <motivo>)`.

El `blockedReason` SHALL ser el motivo, hasta el primer punto seguido de espacio o el cierre del paréntesis, recortado a
200 caracteres. Una tarea marcada `[x]` o tachada MUST NOT cambiar de estado por el marcador. La palabra "bloqueada" sin
dos puntos (por ejemplo "si queda bloqueada") MUST NOT activar el marcador.

#### Scenario: Tarea de distriapp declarada bloqueada
- **WHEN** la tarea 4.9 dice "… Bloqueada (2026-10-22): en prod no hay asesores ni usuarios ASESOR, y …"
- **THEN** su estado es `BLOCKED` y su motivo empieza por "en prod no hay asesores ni usuarios ASESOR"

#### Scenario: Mención sin marcador
- **WHEN** una tarea dice "Probar qué pasa si el hub queda bloqueado"
- **THEN** su estado no cambia por el marcador

### Requirement: Una tarea bloqueada no es trabajo disponible
Una tarea `BLOCKED` por el marcador MUST NOT ser la siguiente tarea de un cambio en "Continuar" ni aparecer en
"Accionables ahora". Si un cambio a medias solo tiene tareas abiertas bloqueadas, MUST NOT aparecer en "Continuar". Su
avance `done/total` no cambia.

#### Scenario: Cambio con su última tarea bloqueada
- **WHEN** a `distriapp/mvp-e2e-sin-mocks` solo le queda abierta la 4.9 y está bloqueada por el marcador
- **THEN** el cambio no aparece en "Continuar" y sigue mostrando su avance en la página del proyecto

### Requirement: Recalcular los proyectos ya guardados
Al desplegar esta regla, el siguiente sync SHALL recalcular todos los proyectos guardados aunque su contenido no haya
cambiado.

#### Scenario: Primer sync después del despliegue
- **WHEN** corre el primer sync con la regla nueva y ningún repo cambió
- **THEN** los proyectos se recalculan y la tarea 4.9 de distriapp queda `BLOCKED`
