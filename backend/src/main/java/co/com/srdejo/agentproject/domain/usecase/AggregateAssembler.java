package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.model.Change;
import co.com.srdejo.agentproject.domain.model.ProgressPoint;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.ProjectAggregate;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.Stage;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.BlockerIn;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.ChangeIn;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.StageIn;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.TaskIn;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.TaskOut;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the stored state of a project from its snapshot (pure). A change reported as unreadable keeps the
 * version found in {@code previous}, artifacts and tasks included; a never-seen unreadable change is stored empty.
 */
final class AggregateAssembler {

    private AggregateAssembler() {
    }

    record Assembled(ProjectAggregate aggregate, ProgressPoint point) {
    }

    static Assembled assemble(ProjectSnapshot snapshot, ProjectAggregate previous, Instant at) {
        List<PendingChange> pending = pendingChanges(snapshot, previous, at);
        List<ProjectSnapshot.Stage> rawStages = snapshot.roadmap() == null ? List.of() : snapshot.roadmap().stages();

        ProgressCalculator.Result result = ProgressCalculator.calculate(new ProgressCalculator.Input(
                rawStages.stream().map(s -> new StageIn(s.key(), s.title(), s.marker(), s.hasCriteria(), s.backlog(),
                        s.tasks().stream().map(t -> new TaskIn(t.checked(), t.cancelled(), t.text())).toList())).toList(),
                pending.stream().map(pc -> new ChangeIn(pc.change().name(), pc.change().archived(),
                        pc.tasks().stream().map(t -> new TaskIn(t.checked(), t.cancelled(), t.text())).toList())).toList(),
                objectiveScope(snapshot),
                snapshot.blockers().stream().map(b -> new BlockerIn(b.id(), b.affects())).toList()));

        List<Stage> stages = new ArrayList<>();
        List<Task> tasks = new ArrayList<>();
        int ordinal = 0;
        for (int i = 0; i < rawStages.size(); i++) {
            ProjectSnapshot.Stage s = rawStages.get(i);
            stages.add(new Stage(i, s.key(), s.title(), s.marker(), s.hasCriteria(), s.backlog()));
            for (int j = 0; j < s.tasks().size(); j++) {
                TaskOut out = result.stageTasks().get(i).get(j);
                if (out == null) {
                    continue;
                }
                ProjectSnapshot.RoadmapTask t = s.tasks().get(j);
                tasks.add(new Task(null, snapshot.id(), Task.TaskSource.ROADMAP, i, null, ordinal++, null, t.text(), null,
                        t.line(),
                        t.checked(), t.cancelled(), out.status(), out.blockedReason(), out.inObjective(),
                        out.heldByBlocker()));
            }
        }
        for (int i = 0; i < pending.size(); i++) {
            PendingChange pc = pending.get(i);
            for (int j = 0; j < pc.tasks().size(); j++) {
                TaskOut out = result.changeTasks().get(i).get(j);
                ProjectSnapshot.ChangeTask t = pc.tasks().get(j);
                tasks.add(new Task(null, snapshot.id(), Task.TaskSource.OPENSPEC, null, pc.change().name(), ordinal++,
                        t.number(),
                        t.text(), t.section(), t.line(), t.checked(), t.cancelled(), out.status(), out.blockedReason(),
                        out.inObjective(), out.heldByBlocker()));
            }
        }

        Project project = project(snapshot, previous, result, at);
        ProgressPoint point = project.objective() == null ? null : new ProgressPoint(project.id(),
                project.objective().name(), project.objective().done(), project.objective().total(), at);
        return new Assembled(new ProjectAggregate(project, stages, pending.stream().map(PendingChange::change).toList(),
                tasks), point);
    }

    private static Project project(ProjectSnapshot s, ProjectAggregate previous, ProgressCalculator.Result result,
                                   Instant at) {
        ProjectSnapshot.Roadmap roadmap = s.roadmap();
        ProjectSnapshot.Objective o = roadmap == null ? null : roadmap.objective();
        Project.Objective objective = o == null ? null : new Project.Objective(o.name(), o.criteria(), o.targetDate(),
                result.scope().stream().map(r -> new Project.ScopeRef(r.ref(), r.kind().name(), r.resolved())).toList(),
                result.objective().done(), result.objective().total());
        List<Project.AchievedObjective> achieved = roadmap == null ? List.of() : roadmap.achieved().stream()
                .map(a -> new Project.AchievedObjective(a.date() == null ? null : a.date().toString(), a.name(),
                        a.criteria()))
                .toList();
        List<Project.Blocker> blockers = s.blockers().stream()
                .map(b -> new Project.Blocker(b.id(), b.type(), b.description(), b.impact(), b.affects(), b.owner(),
                        b.created() == null ? null : b.created().toString(), b.resolution(),
                        result.blockerOpenTasks().get(b.id())))
                .toList();
        ProjectSnapshot.OpenSpec openspec = s.openspec();
        return new Project(s.id(), s.name(), s.repo(), s.alias(), s.summary(), s.state(), s.priorityRank(), s.stack(),
                s.contentHash(), ProgressCalculator.RULE_VERSION, s.sourceCommit(),
                s.sourceModifiedAt() == null ? null : s.sourceModifiedAt().toInstant(),
                null, null, null,
                roadmap != null, result.roadmap().done(), result.roadmap().total(),
                objective, achieved, blockers,
                openspec != null, openspec == null ? 0 : openspec.specsCount(),
                openspec == null ? 0 : openspec.archivedCount(), openspec == null ? List.of() : openspec.commands(),
                previous == null ? at : previous.project().createdAt(), at);
    }

    private static List<String> objectiveScope(ProjectSnapshot s) {
        return s.roadmap() == null || s.roadmap().objective() == null ? null : s.roadmap().objective().scope();
    }

    private static List<PendingChange> pendingChanges(ProjectSnapshot snapshot, ProjectAggregate previous, Instant at) {
        List<PendingChange> result = new ArrayList<>();
        if (snapshot.openspec() == null) {
            return result;
        }
        int ordinal = 0;
        for (ProjectSnapshot.Change c : snapshot.openspec().changes()) {
            int ord = ordinal++;
            if (c.error() == null) {
                Change.Artifacts artifacts = artifacts(c.artifacts());
                result.add(new PendingChange(new Change(c.name(), false, ord, c.metadata(), artifacts,
                        Change.indexOf(c.metadata(), artifacts), at, null, null), c.tasks()));
                continue;
            }
            Change kept = previous == null ? null : previous.changes().stream()
                    .filter(pc -> !pc.archived() && pc.name().equals(c.name()))
                    .findFirst().orElse(null);
            if (kept == null) {
                result.add(new PendingChange(new Change(c.name(), false, ord, null, null, List.of(), null, c.error(), at),
                        List.of()));
            } else {
                List<ProjectSnapshot.ChangeTask> keptTasks = previous.tasks().stream()
                        .filter(t -> c.name().equals(t.changeName()))
                        .map(t -> new ProjectSnapshot.ChangeTask(t.number(), t.text(), t.section(), t.line(), t.checked(),
                                t.cancelled()))
                        .toList();
                result.add(new PendingChange(kept.withOrdinal(ord).withParseError(c.error()).withParseErrorAt(at),
                        keptTasks));
            }
        }
        for (ProjectSnapshot.ArchivedChange a : snapshot.openspec().archivedInScope()) {
            result.add(new PendingChange(new Change(a.name(), true, ordinal++, null, null, List.of(), at, null, null),
                    a.tasks()));
        }
        return result;
    }

    private static Change.Artifacts artifacts(ProjectSnapshot.Artifacts a) {
        if (a == null) {
            return new Change.Artifacts(null, null, null, List.of());
        }
        return new Change.Artifacts(artifact(a.proposal()), artifact(a.design()), artifact(a.tasks()),
                a.specs().stream().map(s -> new Change.SpecArtifact(s.path(), s.content(), s.truncated())).toList());
    }

    private static Change.Artifact artifact(ProjectSnapshot.Artifact a) {
        return a == null ? null : new Change.Artifact(a.content(), a.truncated());
    }

    private record PendingChange(Change change, List<ProjectSnapshot.ChangeTask> tasks) {
    }
}
