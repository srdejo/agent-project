package co.com.srdejo.agentproject.domain.spi;

import co.com.srdejo.agentproject.domain.model.ClosedTask;
import co.com.srdejo.agentproject.domain.model.RepoCommit;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public interface IActivityPersistencePort {

    /** Replaces the stored commits of the project, in one transaction. */
    Mono<Void> replaceCommits(String projectId, List<RepoCommit> commits);

    Mono<Void> saveClosed(List<ClosedTask> closed);

    /** Most recent first. */
    Flux<RepoCommit> findCommitsSince(Instant since);

    /** Most recent first. */
    Flux<ClosedTask> findClosedSince(Instant since);
}
