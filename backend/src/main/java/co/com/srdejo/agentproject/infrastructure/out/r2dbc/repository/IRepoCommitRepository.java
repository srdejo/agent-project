package co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository;

import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.RepoCommitEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface IRepoCommitRepository extends ReactiveCrudRepository<RepoCommitEntity, String> {

    @Modifying
    @Query("DELETE FROM repo_commit WHERE project_id = :projectId")
    Mono<Integer> deleteAllOfProject(String projectId);

    @Modifying
    @Query("INSERT INTO repo_commit (project_id, sha, committed_at, subject) VALUES (:projectId, :sha, :committedAt, :subject)")
    Mono<Integer> insert(String projectId, String sha, Instant committedAt, String subject);

    @Query("SELECT * FROM repo_commit WHERE committed_at >= :since ORDER BY committed_at DESC, sha")
    Flux<RepoCommitEntity> findSince(Instant since);
}
