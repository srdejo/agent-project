package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.model.Change;
import co.com.srdejo.agentproject.domain.model.ClosedTask;
import co.com.srdejo.agentproject.domain.model.ProgressPoint;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.ProjectAggregate;
import co.com.srdejo.agentproject.domain.model.RepoCommit;
import co.com.srdejo.agentproject.domain.model.Stage;
import co.com.srdejo.agentproject.domain.model.SyncRun;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.spi.IActivityPersistencePort;
import co.com.srdejo.agentproject.domain.spi.IAgentPresencePort;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import co.com.srdejo.agentproject.domain.spi.ISyncRunPersistencePort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory adapters for use case tests: they behave like the R2DBC ones, without a database. */
final class InMemoryPorts {

    static final class Projects implements IProjectPersistencePort {
        final Map<String, ProjectAggregate> aggregates = new LinkedHashMap<>();
        final List<ProgressPoint> points = new ArrayList<>();
        final List<String> failOn = new ArrayList<>();
        int replaceCalls;
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Mono<ProjectAggregate> findAggregate(String projectId) {
            return Mono.justOrEmpty(aggregates.get(projectId));
        }

        @Override
        public Mono<Void> replace(ProjectAggregate aggregate, ProgressPoint point) {
            if (failOn.contains(aggregate.project().id())) {
                return Mono.error(new IllegalStateException("fallo simulado"));
            }
            replaceCalls++;
            List<Task> withIds = aggregate.tasks().stream()
                    .map(t -> new Task(ids.incrementAndGet(), t.projectId(), t.source(), t.stageOrdinal(), t.changeName(),
                            t.ordinal(), t.number(), t.text(), t.section(), t.line(), t.checked(), t.cancelled(),
                            t.status(), t.blockedReason(), t.inObjective(), t.heldByBlocker()))
                    .toList();
            aggregates.put(aggregate.project().id(),
                    new ProjectAggregate(aggregate.project(), aggregate.stages(), aggregate.changes(), withIds));
            if (point != null) {
                points.add(point);
            }
            return Mono.empty();
        }

        @Override
        public Mono<Project> saveProject(Project project) {
            ProjectAggregate a = aggregates.get(project.id());
            aggregates.put(project.id(), new ProjectAggregate(project, a.stages(), a.changes(), a.tasks()));
            return Mono.just(project);
        }

        @Override
        public Mono<Project> findProject(String projectId) {
            return findAggregate(projectId).map(ProjectAggregate::project);
        }

        @Override
        public Flux<Project> findAllProjects() {
            return Flux.fromIterable(aggregates.values()).map(ProjectAggregate::project);
        }

        @Override
        public Flux<Stage> findStages(String projectId) {
            return findAggregate(projectId).flatMapMany(a -> Flux.fromIterable(a.stages()));
        }

        @Override
        public Flux<Task> findTasks(String projectId) {
            return findAggregate(projectId).flatMapMany(a -> Flux.fromIterable(a.tasks()));
        }

        @Override
        public Flux<Task> findAllTasks() {
            return Flux.fromIterable(aggregates.values()).flatMapIterable(ProjectAggregate::tasks);
        }

        @Override
        public Flux<Change> findChanges(String projectId) {
            return findAggregate(projectId).flatMapMany(a -> Flux.fromIterable(a.changes())).map(c -> c.withArtifacts(null));
        }

        @Override
        public Flux<ProjectChange> findAllChanges() {
            return Flux.fromIterable(aggregates.values())
                    .flatMapIterable(a -> a.changes().stream()
                            .map(c -> new ProjectChange(a.project().id(), c.withArtifacts(null))).toList());
        }

        @Override
        public Mono<Change> findChange(String projectId, String name) {
            return findAggregate(projectId).flatMapMany(a -> Flux.fromIterable(a.changes()))
                    .filter(c -> c.name().equals(name)).next();
        }

        @Override
        public Flux<ProgressPoint> findProgress(String projectId, String objectiveName) {
            return Flux.fromIterable(points).filter(p -> p.projectId().equals(projectId)
                    && p.objectiveName().equals(objectiveName));
        }

        @Override
        public Flux<ProgressPoint> findRecentProgress(int limit) {
            return Flux.fromIterable(points.stream().sorted(Comparator.comparing(ProgressPoint::takenAt).reversed())
                    .limit(limit).toList());
        }
    }

    static final class Runs implements ISyncRunPersistencePort {
        final List<SyncRun> saved = new ArrayList<>();

        @Override
        public Mono<SyncRun> save(SyncRun run) {
            saved.add(run);
            return Mono.just(run);
        }

        @Override
        public Flux<SyncRun> findLatest(int limit) {
            return Flux.fromIterable(saved.reversed()).take(limit);
        }
    }

    static final class Activity implements IActivityPersistencePort {
        final Map<String, List<RepoCommit>> commits = new LinkedHashMap<>();
        final List<ClosedTask> closed = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Mono<Void> replaceCommits(String projectId, List<RepoCommit> list) {
            commits.put(projectId, List.copyOf(list));
            return Mono.empty();
        }

        @Override
        public Mono<Void> saveClosed(List<ClosedTask> list) {
            list.forEach(c -> closed.add(new ClosedTask(ids.incrementAndGet(), c.projectId(), c.source(), c.scope(),
                    c.number(), c.text(), c.closedAt())));
            return Mono.empty();
        }

        @Override
        public Flux<RepoCommit> findCommitsSince(Instant since) {
            return Flux.fromIterable(commits.values().stream().flatMap(List::stream)
                    .filter(c -> !c.committedAt().isBefore(since))
                    .sorted(Comparator.comparing(RepoCommit::committedAt).reversed()).toList());
        }

        @Override
        public Flux<ClosedTask> findClosedSince(Instant since) {
            return Flux.fromIterable(closed.stream().filter(c -> !c.closedAt().isBefore(since))
                    .sorted(Comparator.comparing(ClosedTask::closedAt).reversed()).toList());
        }
    }

    static final class Agent implements IAgentPresencePort {
        boolean online;

        @Override
        public boolean isAgentOnline() {
            return online;
        }
    }

    private InMemoryPorts() {
    }
}
