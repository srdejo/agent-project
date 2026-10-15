package co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository;

import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.ObjectiveProgressPointEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface IObjectiveProgressPointRepository extends ReactiveCrudRepository<ObjectiveProgressPointEntity, Long> {

    Flux<ObjectiveProgressPointEntity> findByProjectIdAndObjectiveNameOrderByTakenAt(String projectId,
                                                                                     String objectiveName);

    @Query("SELECT * FROM objective_progress_point ORDER BY taken_at DESC LIMIT :limit")
    Flux<ObjectiveProgressPointEntity> findRecent(int limit);
}
