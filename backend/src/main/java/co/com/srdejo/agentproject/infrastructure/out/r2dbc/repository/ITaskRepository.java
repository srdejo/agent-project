package co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository;

import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.TaskEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ITaskRepository extends ReactiveCrudRepository<TaskEntity, Long> {

    Flux<TaskEntity> findByProjectIdOrderByOrdinal(String projectId);

    Flux<TaskEntity> findAllByOrderByProjectIdAscOrdinalAsc();

    @Modifying
    @Query("DELETE FROM task WHERE project_id = :projectId")
    Mono<Integer> deleteAllOfProject(String projectId);
}
