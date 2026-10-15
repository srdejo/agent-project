package co.com.srdejo.agentproject.domain.model.view;

import co.com.srdejo.agentproject.domain.model.Change;

import java.time.Instant;
import java.util.List;

/** Artifacts are raw Markdown: clients must render them as text, never as HTML. */
public record ChangeDetail(
        String projectId,
        String projectName,
        String repo,
        String name,
        boolean archived,
        String path,
        String metadata,
        Change.Artifacts artifacts,
        List<TaskView> tasks,
        int done,
        int total,
        boolean inObjective,
        String parseError,
        Instant parseErrorAt,
        Instant lastParsedAt,
        List<String> commands,
        String evidence
) {
}
