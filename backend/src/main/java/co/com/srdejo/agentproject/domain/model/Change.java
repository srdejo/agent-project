package co.com.srdejo.agentproject.domain.model;

import lombok.With;

import java.time.Instant;
import java.util.List;

/**
 * An OpenSpec change. {@code artifacts} is {@code null} for archived changes (only their tasks travel) and in
 * listings, which never load the artifact contents; {@code artifactIndex} says which ones exist either way.
 */
@With
public record Change(
        String name,
        boolean archived,
        int ordinal,
        String metadata,
        Artifacts artifacts,
        List<String> artifactIndex,
        Instant lastParsedAt,
        String parseError,
        Instant parseErrorAt
) {

    public static List<String> indexOf(String metadata, Artifacts artifacts) {
        List<String> index = new java.util.ArrayList<>();
        if (artifacts != null) {
            if (artifacts.proposal() != null) {
                index.add("proposal");
            }
            if (artifacts.design() != null) {
                index.add("design");
            }
            if (artifacts.tasks() != null) {
                index.add("tasks");
            }
            artifacts.specs().forEach(s -> index.add(s.path()));
        }
        if (metadata != null) {
            index.add("metadata");
        }
        return index;
    }

    public record Artifacts(Artifact proposal, Artifact design, Artifact tasks, List<SpecArtifact> specs) {
    }

    public record Artifact(String content, boolean truncated) {
    }

    public record SpecArtifact(String path, String content, boolean truncated) {
    }
}
