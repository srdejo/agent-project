package co.com.srdejo.agentproject.application.dto.response;

import java.time.Instant;
import java.util.List;

public record ChangeListResponseDto(
        boolean openspecDetected,
        int specsCount,
        int archivedCount,
        List<String> commands,
        List<ChangeSummaryDto> changes
) {

    /** {@code artifacts}: the present ones (proposal, design, tasks, metadata, spec paths). */
    public record ChangeSummaryDto(String name, boolean archived, List<String> artifacts, int done, int total,
                                   boolean inObjective, String parseError, Instant parseErrorAt,
                                   Instant lastParsedAt) {
    }
}
