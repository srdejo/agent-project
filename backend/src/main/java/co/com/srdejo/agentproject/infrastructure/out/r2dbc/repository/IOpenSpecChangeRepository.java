package co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository;

import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.OpenSpecChangeEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IOpenSpecChangeRepository extends ReactiveCrudRepository<OpenSpecChangeEntity, Long> {

    String WITHOUT_ARTIFACTS = "SELECT id, project_id, name, archived, ordinal, metadata, NULL::jsonb AS artifacts, "
            + "artifact_index, last_parsed_at, parse_error, parse_error_at FROM openspec_change ";

    Flux<OpenSpecChangeEntity> findByProjectIdOrderByOrdinal(String projectId);

    Mono<OpenSpecChangeEntity> findByProjectIdAndName(String projectId, String name);

    @Query(WITHOUT_ARTIFACTS + "WHERE project_id = :projectId ORDER BY ordinal")
    Flux<OpenSpecChangeEntity> findSummariesOfProject(String projectId);

    @Query(WITHOUT_ARTIFACTS + "ORDER BY project_id, ordinal")
    Flux<OpenSpecChangeEntity> findAllSummaries();

    @Modifying
    @Query("DELETE FROM openspec_change WHERE project_id = :projectId")
    Mono<Integer> deleteAllOfProject(String projectId);
}
