package co.com.srdejo.agentproject.domain.model;

import lombok.With;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Stored state of a portfolio project: editorial layer, derived counters, current objective and blockers.
 * {@code objective} is {@code null} when the roadmap declares none.
 */
@With
public record Project(
        String id,
        String name,
        String repo,
        String alias,
        String summary,
        String state,
        Integer priorityRank,
        List<String> stack,
        String contentHash,
        int ruleVersion,
        String sourceCommit,
        Instant sourceModifiedAt,
        Instant missingSince,
        String readError,
        Instant readErrorAt,
        boolean roadmapDetected,
        int roadmapDone,
        int roadmapTotal,
        Objective objective,
        List<AchievedObjective> achievedObjectives,
        List<Blocker> blockers,
        boolean openspecDetected,
        int openspecSpecsCount,
        int openspecArchivedCount,
        List<String> openspecCommands,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean isUpToDate(String hash, int currentRuleVersion) {
        return hash.equals(contentHash) && ruleVersion == currentRuleVersion;
    }

    public record Objective(String name, String criteria, LocalDate targetDate, List<ScopeRef> scope, int done,
                            int total) {

        public boolean achieved() {
            return total > 0 && done == total;
        }
    }

    /** {@code kind} is {@code STAGE} or {@code CHANGE}. */
    public record ScopeRef(String ref, String kind, boolean resolved) {
    }

    public record AchievedObjective(String date, String name, String criteria) {
    }

    /** {@code openTasksAffected} is {@code null} when none of {@code affects} resolved to a stage or change. */
    public record Blocker(String id, String type, String description, String impact, List<String> affects,
                          String owner, String created, String resolution, Integer openTasksAffected) {
    }
}
