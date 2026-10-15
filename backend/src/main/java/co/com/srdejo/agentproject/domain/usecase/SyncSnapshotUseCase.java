package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.api.ISyncServicePort;
import co.com.srdejo.agentproject.domain.model.ProjectAggregate;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.SnapshotBatch;
import co.com.srdejo.agentproject.domain.model.SyncOutcome;
import co.com.srdejo.agentproject.domain.model.SyncRun;
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
import java.util.Optional;
import java.util.Set;

/**
 * Applies a snapshot project by project (spec {@code sync-snapshot}): same content and rule version is a no-op,
 * anything else replaces the stored state; a project that fails to apply is rejected without stopping the rest.
 */
public class SyncSnapshotUseCase implements ISyncServicePort {

    private final IProjectPersistencePort projects;
    private final ISyncRunPersistencePort runs;
    private final Clock clock;

    public SyncSnapshotUseCase(IProjectPersistencePort projects, ISyncRunPersistencePort runs, Clock clock) {
        this.projects = projects;
        this.runs = runs;
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
                .concatMap(snapshot -> apply(snapshot, startedAt)
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
                .then(markMissing(present, startedAt))
                .flatMap(missing -> runs.save(new SyncRun(startedAt, elapsed(startedAt), batch.formatVersion(),
                        batch.generatorVersion(), batch.generatedAt().toInstant(), null, created, updated, unchanged,
                        missing, rejected, batch.readErrors(), batch.warnings())));
    }

    @Override
    public Mono<SyncRun> recordFileError(String error, Instant startedAt) {
        return runs.save(SyncRun.fileError(startedAt, elapsed(startedAt), error));
    }

    Mono<SyncOutcome> apply(ProjectSnapshot snapshot, Instant at) {
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
                    return projects.replace(assembled.aggregate(), assembled.point())
                            .thenReturn(existing == null ? SyncOutcome.CREATED : SyncOutcome.UPDATED);
                });
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
