package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.api.ISyncServicePort;
import co.com.srdejo.agentproject.domain.model.ClosedTask;
import co.com.srdejo.agentproject.domain.model.ProjectAggregate;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.RepoCommit;
import co.com.srdejo.agentproject.domain.model.SnapshotBatch;
import co.com.srdejo.agentproject.domain.model.SyncOutcome;
import co.com.srdejo.agentproject.domain.model.SyncRun;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.spi.IActivityPersistencePort;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import co.com.srdejo.agentproject.domain.spi.ISyncRunPersistencePort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Applies a snapshot project by project (spec {@code sync-snapshot}): same content and rule version is a no-op,
 * anything else replaces the stored state; a project that fails to apply is rejected without stopping the rest.
 */
public class SyncSnapshotUseCase implements ISyncServicePort {

    private final IProjectPersistencePort projects;
    private final ISyncRunPersistencePort runs;
    private final IActivityPersistencePort activity;
    private final Clock clock;

    public SyncSnapshotUseCase(IProjectPersistencePort projects, ISyncRunPersistencePort runs,
                               IActivityPersistencePort activity, Clock clock) {
        this.projects = projects;
        this.runs = runs;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    public Mono<SyncRun> applySnapshot(SnapshotBatch batch, Instant startedAt) {
        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        Map<String, String> rejected = new LinkedHashMap<>(batch.rejected());

        Set<String> present = new HashSet<>(batch.rejected().keySet());
        present.addAll(batch.readErrors().keySet());
        batch.valid().forEach(s -> present.add(s.id()));

        return Flux.fromIterable(batch.valid())
                .concatMap(snapshot -> apply(snapshot, startedAt, batch.generatedAt().toInstant())
                        .doOnNext(outcome -> {
                            switch (outcome) {
                                case CREATED -> created.add(snapshot.id());
                                case UPDATED -> updated.add(snapshot.id());
                                case UNCHANGED -> unchanged.add(snapshot.id());
                            }
                        })
                        .onErrorResume(e -> {
                            rejected.put(snapshot.id(), "error al aplicar: " + e.getMessage());
                            return Mono.empty();
                        }))
                .thenMany(Flux.fromIterable(batch.readErrors().entrySet())
                        .concatMap(e -> recordReadError(e.getKey(), e.getValue(), startedAt)))
                .thenMany(Flux.fromIterable(batch.activity() == null ? List.<Map.Entry<String, List<RepoCommit>>>of()
                                : batch.activity().entrySet())
                        .concatMap(e -> activity.replaceCommits(e.getKey(), e.getValue())))
                .then(markMissing(present, startedAt))
                .flatMap(missing -> runs.save(new SyncRun(startedAt, elapsed(startedAt), batch.formatVersion(),
                        batch.generatorVersion(), batch.generatedAt().toInstant(), null, created, updated, unchanged,
                        missing, rejected, batch.readErrors(), batch.warnings())));
    }

    @Override
    public Mono<SyncRun> recordFileError(String error, Instant startedAt) {
        return runs.save(SyncRun.fileError(startedAt, elapsed(startedAt), error));
    }

    Mono<SyncOutcome> apply(ProjectSnapshot snapshot, Instant at, Instant generatedAt) {
        return projects.findAggregate(snapshot.id())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(previous -> {
                    ProjectAggregate existing = previous.orElse(null);
                    if (existing != null
                            && existing.project().isUpToDate(snapshot.contentHash(), ProgressCalculator.RULE_VERSION)) {
                        return projects.saveProject(existing.project().withMissingSince(null).withReadError(null)
                                .withReadErrorAt(null)).thenReturn(SyncOutcome.UNCHANGED);
                    }
                    AggregateAssembler.Assembled assembled = AggregateAssembler.assemble(snapshot, existing, at);
                    List<ClosedTask> closed = existing == null ? List.of()
                            : closedBetween(existing, assembled.aggregate(), generatedAt);
                    return projects.replace(assembled.aggregate(), assembled.point())
                            .then(closed.isEmpty() ? Mono.<Void>empty() : activity.saveClosed(closed))
                            .thenReturn(existing == null ? SyncOutcome.CREATED : SyncOutcome.UPDATED);
                });
    }

    /** Tasks open before and done now, matched by a key that survives the full replacement (ids do not). */
    static List<ClosedTask> closedBetween(ProjectAggregate before, ProjectAggregate after, Instant closedAt) {
        Set<String> openBefore = new HashSet<>();
        for (Task t : before.tasks()) {
            if (!t.checked() && !t.cancelled()) {
                openBefore.add(key(t, scope(t, before)));
            }
        }
        List<ClosedTask> closed = new ArrayList<>();
        for (Task t : after.tasks()) {
            String scope = scope(t, after);
            if (t.checked() && !t.cancelled() && openBefore.contains(key(t, scope))) {
                closed.add(new ClosedTask(null, after.project().id(), t.source(), scope, t.number(), t.text(),
                        closedAt));
            }
        }
        return closed;
    }

    private static String scope(Task t, ProjectAggregate aggregate) {
        if (t.source() == Task.TaskSource.OPENSPEC) {
            return t.changeName();
        }
        return aggregate.stages().stream().filter(s -> Objects.equals(s.ordinal(), t.stageOrdinal())).findFirst()
                .map(s -> s.key() != null ? s.key() : s.title()).orElse("");
    }

    private static String key(Task t, String scope) {
        boolean numbered = t.source() == Task.TaskSource.OPENSPEC && t.number() != null && !t.number().isBlank();
        return t.source() + "|" + scope + "|" + (numbered ? t.number() : t.text());
    }

    private Mono<Void> recordReadError(String id, String error, Instant at) {
        return projects.findProject(id)
                .flatMap(p -> projects.saveProject(p.withReadError(error).withReadErrorAt(at).withMissingSince(null)))
                .then();
    }

    private Mono<List<String>> markMissing(Set<String> present, Instant at) {
        return projects.findAllProjects()
                .filter(p -> !present.contains(p.id()) && p.missingSince() == null)
                .concatMap(p -> projects.saveProject(p.withMissingSince(at)))
                .map(p -> p.id())
                .collectList();
    }

    private long elapsed(Instant startedAt) {
        return Math.max(0, Duration.between(startedAt, clock.instant()).toMillis());
    }
}
