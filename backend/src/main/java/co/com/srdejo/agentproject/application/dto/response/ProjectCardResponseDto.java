package co.com.srdejo.agentproject.application.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * One project of the portfolio. {@code objective} is null when no objective is declared ("Sin objetivo declarado");
 * {@code objective.percent} is null when the scope has no countable tasks ("Sin datos").
 */
public record ProjectCardResponseDto(
        String id,
        String name,
        String alias,
        String summary,
        String state,
        Integer priorityRank,
        List<String> stack,
        ObjectiveSummaryDto objective,
        CountsDto roadmap,
        int activeChanges,
        boolean openspecDetected,
        int blockerCount,
        BlockerResponseDto mainBlocker,
        Instant lastActivity,
        Instant missingSince,
        String readError,
        Instant readErrorAt,
        boolean hasActionable
) {

    public record ObjectiveSummaryDto(String name, int done, int total, Integer percent, boolean achieved) {
    }

    public record CountsDto(int done, int total, Integer percent) {
    }
}
