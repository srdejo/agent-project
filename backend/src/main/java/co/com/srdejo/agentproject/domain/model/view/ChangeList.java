package co.com.srdejo.agentproject.domain.model.view;

import java.time.Instant;
import java.util.List;

public record ChangeList(
        boolean openspecDetected,
        int specsCount,
        int archivedCount,
        List<String> commands,
        List<ChangeSummary> changes
) {

    /** {@code artifacts} lists the present ones: proposal, design, tasks, metadata and each spec path. */
    public record ChangeSummary(String name, boolean archived, List<String> artifacts, int done, int total,
                                boolean inObjective, String parseError, Instant parseErrorAt, Instant lastParsedAt) {
    }
}
