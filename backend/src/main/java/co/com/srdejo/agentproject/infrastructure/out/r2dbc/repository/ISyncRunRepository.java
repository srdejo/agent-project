package co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository;

import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.SyncRunEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ISyncRunRepository extends ReactiveCrudRepository<SyncRunEntity, Long> {

    @Query("SELECT * FROM sync_run ORDER BY started_at DESC, id DESC LIMIT :limit")
    Flux<SyncRunEntity> findLatest(int limit);
}
