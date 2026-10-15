package co.com.srdejo.agentproject.infrastructure.out.r2dbc.adapter;

import co.com.srdejo.agentproject.domain.model.SyncRun;
import co.com.srdejo.agentproject.domain.spi.ISyncRunPersistencePort;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.mapper.R2dbcEntityMapper;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.ISyncRunRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class SyncRunR2dbcAdapter implements ISyncRunPersistencePort {

    private final ISyncRunRepository syncRunRepository;
    private final R2dbcEntityMapper mapper;

    @Override
    public Mono<SyncRun> save(SyncRun run) {
        return syncRunRepository.save(mapper.toEntity(run)).map(mapper::toRun);
    }

    @Override
    public Flux<SyncRun> findLatest(int limit) {
        return syncRunRepository.findLatest(limit).map(mapper::toRun);
    }
}
