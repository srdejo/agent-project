package co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository;

import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.TaskClosedEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface ITaskClosedRepository extends ReactiveCrudRepository<TaskClosedEntity, Long> {

    @Query("SELECT * FROM task_closed WHERE closed_at >= :since ORDER BY closed_at DESC, id")
    Flux<TaskClosedEntity> findSince(Instant since);
}
