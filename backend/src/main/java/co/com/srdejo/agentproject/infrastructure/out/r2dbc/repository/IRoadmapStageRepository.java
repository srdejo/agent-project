package co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository;

import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.RoadmapStageEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IRoadmapStageRepository extends ReactiveCrudRepository<RoadmapStageEntity, Long> {

    Flux<RoadmapStageEntity> findByProjectIdOrderByOrdinal(String projectId);

    @Modifying
    @Query("DELETE FROM roadmap_stage WHERE project_id = :projectId")
    Mono<Integer> deleteAllOfProject(String projectId);
}
