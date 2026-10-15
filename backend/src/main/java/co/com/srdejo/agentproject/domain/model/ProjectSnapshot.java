package co.com.srdejo.agentproject.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Complete reported state of one project, as the generator read it from the repo files. Only facts:
 * statuses and percentages are derived by {@code ProgressCalculator}. {@code contentHash} is computed by the
 * inbox adapter over the canonical entry JSON.
 */
public record ProjectSnapshot(
        String id,
        String contentHash,
        String name,
        String repo,
        String alias,
        String summary,
        String state,
        Integer priorityRank,
        List<String> stack,
        List<Blocker> blockers,
        String sourceCommit,
        OffsetDateTime sourceModifiedAt,
        Roadmap roadmap,
        OpenSpec openspec
) {

    public record Blocker(String id, String type, String description, String impact, List<String> affects,
                          String owner, LocalDate created, String resolution) {
    }

    public record Roadmap(Objective objective, List<AchievedObjective> achieved, List<Stage> stages) {
    }

    public record Objective(String name, String criteria, List<String> scope, LocalDate targetDate) {
    }

    public record AchievedObjective(LocalDate date, String name, String criteria) {
    }

    public record Stage(String key, String title, String marker, boolean hasCriteria, boolean backlog,
                        List<RoadmapTask> tasks) {
    }

    public record RoadmapTask(String text, int line, boolean checked, boolean cancelled) {
    }

    public record OpenSpec(int specsCount, int archivedCount, List<String> commands, List<Change> changes,
                           List<ArchivedChange> archivedInScope) {
    }

    public record Change(String name, String error, String metadata, Artifacts artifacts, List<ChangeTask> tasks) {
    }

    public record ArchivedChange(String name, List<ChangeTask> tasks) {
    }

    public record Artifacts(Artifact proposal, Artifact design, Artifact tasks, List<SpecArtifact> specs) {
    }

    public record Artifact(String content, boolean truncated) {
    }

    public record SpecArtifact(String path, String content, boolean truncated) {
    }

    public record ChangeTask(String number, String text, String section, int line, boolean checked,
                             boolean cancelled) {
    }
}
