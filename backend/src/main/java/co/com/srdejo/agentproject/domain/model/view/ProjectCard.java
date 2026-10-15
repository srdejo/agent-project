package co.com.srdejo.agentproject.domain.model.view;

import co.com.srdejo.agentproject.domain.model.Project;

import java.time.Instant;
import java.util.List;

/**
 * One project of the portfolio. {@code objective} is {@code null} when none is declared, and its
 * {@code percent} is {@code null} when the scope has no countable tasks ("Sin datos").
 */
public record ProjectCard(
        String id,
        String name,
        String alias,
        String summary,
        String state,
        Integer priorityRank,
        List<String> stack,
        ObjectiveSummary objective,
        Counts roadmap,
        int activeChanges,
        boolean openspecDetected,
        int blockerCount,
        Project.Blocker mainBlocker,
        Instant lastActivity,
        Instant missingSince,
        String readError,
        Instant readErrorAt,
        boolean hasActionable
) {

    public record ObjectiveSummary(String name, int done, int total, Integer percent, boolean achieved) {
    }

    public record Counts(int done, int total, Integer percent) {
    }

    public static Integer percent(int done, int total) {
        return total == 0 ? null : (int) Math.round(100.0 * done / total);
    }
}
