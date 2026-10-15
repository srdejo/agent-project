package co.com.srdejo.agentproject.application.dto.response;

import java.time.Instant;
import java.util.List;

/** Artifacts are raw Markdown: render them as text, never as HTML. A missing artifact is null. */
public record ChangeDetailResponseDto(
        String projectId,
        String projectName,
        String repo,
        String name,
        boolean archived,
        String path,
        String metadata,
        ArtifactsDto artifacts,
        List<TaskResponseDto> tasks,
        int done,
        int total,
        boolean inObjective,
        String parseError,
        Instant parseErrorAt,
        Instant lastParsedAt,
        List<String> commands,
        String evidence
) {

    public record ArtifactsDto(ArtifactDto proposal, ArtifactDto design, ArtifactDto tasks,
                               List<SpecArtifactDto> specs) {
    }

    public record ArtifactDto(String content, boolean truncated) {
    }

    public record SpecArtifactDto(String path, String content, boolean truncated) {
    }
}
