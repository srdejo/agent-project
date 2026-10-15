package co.com.srdejo.agentproject.domain.model.view;

/** {@code origin} is the stage title (ROADMAP) or the change name (OPENSPEC). */
public record TaskView(
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
