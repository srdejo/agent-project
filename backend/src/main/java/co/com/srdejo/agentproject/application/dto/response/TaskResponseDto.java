package co.com.srdejo.agentproject.application.dto.response;

/** {@code status}: OPEN, IN_PROGRESS, BLOCKED, DONE or CANCELLED. DONE = completed according to the repo. */
public record TaskResponseDto(
        Long id,
        String source,
        String origin,
        String number,
        String text,
        String section,
        int line,
        String status,
        String blockedReason,
        boolean inObjective,
        String heldByBlocker
) {
}
