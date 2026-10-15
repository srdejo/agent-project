package co.com.srdejo.agentproject.domain.model;

/**
 * A checkbox of the project. A ROADMAP task points at its stage by {@code stageOrdinal}; an OpenSpec task at its
 * change by {@code changeName}. {@code status}, {@code blockedReason}, {@code inObjective} and
 * {@code heldByBlocker} are derived by {@code ProgressCalculator} when the snapshot is applied.
 */
public record Task(
        Long id,
        String projectId,
        TaskSource source,
        Integer stageOrdinal,
        String changeName,
        int ordinal,
        String number,
        String text,
        String section,
        int line,
        boolean checked,
        boolean cancelled,
        TaskStatus status,
        String blockedReason,
        boolean inObjective,
        String heldByBlocker
) {

    public enum TaskSource {ROADMAP, OPENSPEC}
}
