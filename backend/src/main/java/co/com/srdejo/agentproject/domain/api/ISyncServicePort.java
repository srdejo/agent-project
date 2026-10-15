package co.com.srdejo.agentproject.domain.api;

import co.com.srdejo.agentproject.domain.model.SnapshotBatch;
import co.com.srdejo.agentproject.domain.model.SyncRun;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ISyncServicePort {

    /** Applies every valid entry, records unreadable and missing projects, and stores the run. */
    Mono<SyncRun> applySnapshot(SnapshotBatch batch, Instant startedAt);

    /** The inbox file itself could not be used: nothing is applied, only the run is stored. */
    Mono<SyncRun> recordFileError(String error, Instant startedAt);
}
