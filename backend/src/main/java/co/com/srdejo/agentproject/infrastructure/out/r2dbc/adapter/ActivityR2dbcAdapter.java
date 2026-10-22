package co.com.srdejo.agentproject.infrastructure.out.r2dbc.adapter;

import co.com.srdejo.agentproject.domain.model.ClosedTask;
import co.com.srdejo.agentproject.domain.model.RepoCommit;
import co.com.srdejo.agentproject.domain.spi.IActivityPersistencePort;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.mapper.R2dbcEntityMapper;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IRepoCommitRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.ITaskClosedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class ActivityR2dbcAdapter implements IActivityPersistencePort {

    private final IRepoCommitRepository commitRepository;
    private final ITaskClosedRepository closedRepository;
    private final R2dbcEntityMapper mapper;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<Void> replaceCommits(String projectId, List<RepoCommit> commits) {
        Mono<Void> work = commitRepository.deleteAllOfProject(projectId)
                .thenMany(Flux.fromIterable(commits)
                        .concatMap(c -> commitRepository.insert(projectId, c.sha(), c.committedAt(), c.subject())))
                .then();
        return transactionalOperator.transactional(work);
    }

    @Override
    public Mono<Void> saveClosed(List<ClosedTask> closed) {
        return closedRepository.saveAll(closed.stream().map(mapper::toEntity).toList()).then();
    }

    @Override
    public Flux<RepoCommit> findCommitsSince(Instant since) {
        return commitRepository.findSince(since).map(mapper::toCommit);
    }

    @Override
    public Flux<ClosedTask> findClosedSince(Instant since) {
        return closedRepository.findSince(since).map(mapper::toClosed);
    }
}
