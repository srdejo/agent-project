package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.api.ISyncHealthServicePort;
import co.com.srdejo.agentproject.domain.model.SyncRun;
import co.com.srdejo.agentproject.domain.model.view.SyncHealth;
import co.com.srdejo.agentproject.domain.spi.IAgentPresencePort;
import co.com.srdejo.agentproject.domain.spi.ISyncRunPersistencePort;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Spec {@code sync-health}: data age comes from the last successful run's {@code generated_at}. */
public class SyncHealthUseCase implements ISyncHealthServicePort {

    /** How far back to look for the last successful run. */
    private static final int SUCCESS_LOOKBACK = 200;

    private final ISyncRunPersistencePort runs;
    private final IAgentPresencePort agent;
    private final Duration staleAfter;
    private final Clock clock;

    public SyncHealthUseCase(ISyncRunPersistencePort runs, IAgentPresencePort agent, Duration staleAfter, Clock clock) {
        this.runs = runs;
        this.agent = agent;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    @Override
    public Mono<SyncHealth> health() {
        return runs.findLatest(SUCCESS_LOOKBACK).collectList().map(this::health);
    }

    private SyncHealth health(List<SyncRun> latest) {
        Optional<SyncRun> last = latest.stream().findFirst();
        Optional<SyncRun> successful = latest.stream().filter(SyncRun::isSuccessful).findFirst();
        Optional<Instant> generatedAt = successful.map(r -> r.generatedAt() != null ? r.generatedAt() : r.startedAt());
        Long age = generatedAt.map(g -> Duration.between(g, clock.instant()).toSeconds()).orElse(null);
        return new SyncHealth(
                last.map(SyncRun::startedAt).orElse(null),
                successful.map(SyncRun::startedAt).orElse(null),
                generatedAt.orElse(null),
                last.orElse(null),
                age,
                age == null || age > staleAfter.toSeconds(),
                staleAfter.toSeconds(),
                agent.isAgentOnline() ? "online" : "offline");
    }
}
