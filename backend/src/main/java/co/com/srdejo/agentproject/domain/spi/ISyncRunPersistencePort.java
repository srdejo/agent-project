package co.com.srdejo.agentproject.domain.spi;

import co.com.srdejo.agentproject.domain.model.SyncRun;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ISyncRunPersistencePort {

    Mono<SyncRun> save(SyncRun run);

    /** Most recent first. */
    Flux<SyncRun> findLatest(int limit);
}
