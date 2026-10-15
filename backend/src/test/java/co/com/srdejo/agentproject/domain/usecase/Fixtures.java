package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.ChangeTask;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.RoadmapTask;
import co.com.srdejo.agentproject.domain.model.SnapshotBatch;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small builders for snapshots used across the use case tests. */
final class Fixtures {

    static final OffsetDateTime GENERATED_AT = OffsetDateTime.parse("2026-10-15T07:00:00-05:00");

    private Fixtures() {
    }

    static RoadmapTask rt(String text, boolean checked) {
        return new RoadmapTask(text, 1, checked, false);
    }

    static ChangeTask ct(String number, String text, boolean checked) {
        return new ChangeTask(number, text, "1. Grupo", 1, checked, false);
    }

    static ProjectSnapshot.Stage stage(String key, String marker, RoadmapTask... tasks) {
        return new ProjectSnapshot.Stage(key, key + " — etapa", marker, true, false, List.of(tasks));
    }

    static ProjectSnapshot.Change change(String name, ChangeTask... tasks) {
        return new ProjectSnapshot.Change(name, null, "schema: spec-driven",
                new ProjectSnapshot.Artifacts(new ProjectSnapshot.Artifact("# Proposal " + name, false), null,
                        new ProjectSnapshot.Artifact("# Tasks", false), List.of()),
                List.of(tasks));
    }

    static ProjectSnapshot.Change brokenChange(String name) {
        return new ProjectSnapshot.Change(name, "ilegible", null, null, List.of());
    }

    static Builder project(String id) {
        return new Builder(id);
    }

    static SnapshotBatch batch(ProjectSnapshot... projects) {
        return new SnapshotBatch(2, "test", GENERATED_AT, List.of(projects), Map.of(), Map.of(), Map.of());
    }

    static final class Builder {
        private final String id;
        private String state = "ACTIVE";
        private Integer rank;
        private String hash = "h1";
        private ProjectSnapshot.Objective objective;
        private final List<ProjectSnapshot.Stage> stages = new ArrayList<>();
        private final List<ProjectSnapshot.Change> changes = new ArrayList<>();
        private final List<ProjectSnapshot.ArchivedChange> archived = new ArrayList<>();
        private final List<ProjectSnapshot.Blocker> blockers = new ArrayList<>();
        private List<String> commands = List.of("apply", "archive");
        private boolean openspec = true;
        private boolean roadmap = true;

        Builder(String id) {
            this.id = id;
        }

        Builder state(String state) { this.state = state; return this; }
        Builder rank(Integer rank) { this.rank = rank; return this; }
        Builder hash(String hash) { this.hash = hash; return this; }
        Builder objective(String name, String... scope) {
            this.objective = new ProjectSnapshot.Objective(name, "criterio de " + name, List.of(scope), null);
            return this;
        }
        Builder stage(ProjectSnapshot.Stage s) { stages.add(s); return this; }
        Builder change(ProjectSnapshot.Change c) { changes.add(c); return this; }
        Builder archived(String name, ChangeTask... tasks) {
            archived.add(new ProjectSnapshot.ArchivedChange(name, List.of(tasks)));
            return this;
        }
        Builder decision(String blockerId, String... affects) {
            blockers.add(new ProjectSnapshot.Blocker(blockerId, "PRODUCT_DECISION", "Decidir " + blockerId, null,
                    List.of(affects), "Daniel", null, null));
            return this;
        }
        Builder commands(String... commands) { this.commands = List.of(commands); return this; }
        Builder noOpenSpec() { this.openspec = false; return this; }
        Builder noRoadmap() { this.roadmap = false; return this; }

        ProjectSnapshot build() {
            return new ProjectSnapshot(id, hash, "Proyecto " + id, id, null, null, state, rank, List.of("Java"),
                    blockers, "abc123", GENERATED_AT,
                    roadmap ? new ProjectSnapshot.Roadmap(objective, List.of(), stages) : null,
                    openspec ? new ProjectSnapshot.OpenSpec(1, archived.size(), commands, changes, archived) : null);
        }
    }
}
