package co.com.srdejo.agentproject.infrastructure.configuration;

import co.com.srdejo.agentproject.domain.api.IContinuationServicePort;
import co.com.srdejo.agentproject.domain.api.IDailyServicePort;
import co.com.srdejo.agentproject.domain.api.IPortfolioServicePort;
import co.com.srdejo.agentproject.domain.api.ISyncHealthServicePort;
import co.com.srdejo.agentproject.domain.api.ISyncServicePort;
import co.com.srdejo.agentproject.domain.api.IWorkServicePort;
import co.com.srdejo.agentproject.domain.spi.IActivityPersistencePort;
import co.com.srdejo.agentproject.domain.spi.IAgentPresencePort;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import co.com.srdejo.agentproject.domain.spi.ISyncRunPersistencePort;
import co.com.srdejo.agentproject.domain.usecase.ContinuationUseCase;
import co.com.srdejo.agentproject.domain.usecase.DailyUseCase;
import co.com.srdejo.agentproject.domain.usecase.PortfolioUseCase;
import co.com.srdejo.agentproject.domain.usecase.SyncHealthUseCase;
import co.com.srdejo.agentproject.domain.usecase.SyncSnapshotUseCase;
import co.com.srdejo.agentproject.domain.usecase.WorkUseCase;
import co.com.srdejo.agentproject.infrastructure.input.inbox.SnapshotParser;
import co.com.srdejo.agentproject.infrastructure.input.websocket.AgentPresenceService;
import co.com.srdejo.agentproject.infrastructure.out.agent.AgentPresenceAdapter;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.adapter.ActivityR2dbcAdapter;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.adapter.ProjectR2dbcAdapter;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.adapter.SyncRunR2dbcAdapter;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.mapper.JsonbCodec;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.mapper.R2dbcEntityMapper;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IObjectiveProgressPointRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IOpenSpecChangeRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IProjectRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IRepoCommitRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IRoadmapStageRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.ISyncRunRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.ITaskClosedRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.ITaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.reactive.TransactionalOperator;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;

/** Wires domain ports to their adapters: the use cases carry no Spring annotations. */
@Configuration
@RequiredArgsConstructor
public class BeanConfiguration {

    private final IProjectRepository projectRepository;
    private final IRoadmapStageRepository stageRepository;
    private final IOpenSpecChangeRepository changeRepository;
    private final ITaskRepository taskRepository;
    private final IObjectiveProgressPointRepository pointRepository;
    private final ISyncRunRepository syncRunRepository;
    private final IRepoCommitRepository commitRepository;
    private final ITaskClosedRepository closedRepository;
    private final TransactionalOperator transactionalOperator;
    private final AgentPresenceService agentPresenceService;
    private final JsonMapper jsonMapper;

    @Value("${agent-project.sync.stale-after}")
    private Duration staleAfter;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SnapshotParser snapshotParser() {
        return new SnapshotParser(jsonMapper);
    }

    @Bean
    public R2dbcEntityMapper r2dbcEntityMapper() {
        return new R2dbcEntityMapper(new JsonbCodec(jsonMapper));
    }

    @Bean
    public IProjectPersistencePort projectPersistencePort() {
        return new ProjectR2dbcAdapter(projectRepository, stageRepository, changeRepository, taskRepository,
                pointRepository, r2dbcEntityMapper(), transactionalOperator);
    }

    @Bean
    public ISyncRunPersistencePort syncRunPersistencePort() {
        return new SyncRunR2dbcAdapter(syncRunRepository, r2dbcEntityMapper());
    }

    @Bean
    public IActivityPersistencePort activityPersistencePort() {
        return new ActivityR2dbcAdapter(commitRepository, closedRepository, r2dbcEntityMapper(), transactionalOperator);
    }

    @Bean
    public IAgentPresencePort agentPresencePort() {
        return new AgentPresenceAdapter(agentPresenceService);
    }

    @Bean
    public ISyncServicePort syncServicePort() {
        return new SyncSnapshotUseCase(projectPersistencePort(), syncRunPersistencePort(), activityPersistencePort(),
                clock());
    }

    @Bean
    public ISyncHealthServicePort syncHealthServicePort() {
        return new SyncHealthUseCase(syncRunPersistencePort(), agentPresencePort(), staleAfter, clock());
    }

    @Bean
    public IPortfolioServicePort portfolioServicePort() {
        return new PortfolioUseCase(projectPersistencePort());
    }

    @Bean
    public IContinuationServicePort continuationServicePort() {
        return new ContinuationUseCase(projectPersistencePort(), syncHealthServicePort());
    }

    @Bean
    public IWorkServicePort workServicePort() {
        return new WorkUseCase(projectPersistencePort(), syncRunPersistencePort());
    }

    @Bean
    public IDailyServicePort dailyServicePort() {
        return new DailyUseCase(workServicePort(), continuationServicePort(), syncHealthServicePort(),
                projectPersistencePort(), activityPersistencePort(), clock());
    }
}
