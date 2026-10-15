package co.com.srdejo.agentproject.application.handler.impl;

import co.com.srdejo.agentproject.application.dto.response.ChangeDetailResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ChangeListResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ContinuationResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ProjectCardResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ProjectDetailResponseDto;
import co.com.srdejo.agentproject.application.handler.IPortfolioHandler;
import co.com.srdejo.agentproject.application.mapper.IPortfolioResponseMapper;
import co.com.srdejo.agentproject.domain.api.IContinuationServicePort;
import co.com.srdejo.agentproject.domain.api.IPortfolioServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PortfolioHandler implements IPortfolioHandler {

    private final IPortfolioServicePort portfolioServicePort;
    private final IContinuationServicePort continuationServicePort;
    private final IPortfolioResponseMapper responseMapper;

    @Override
    public Flux<ProjectCardResponseDto> getPortfolio(boolean includeArchived) {
        return portfolioServicePort.getPortfolio(includeArchived).map(responseMapper::toResponse);
    }

    @Override
    public Mono<ProjectDetailResponseDto> getProject(String projectId) {
        return portfolioServicePort.getProject(projectId).map(responseMapper::toResponse);
    }

    @Override
    public Mono<ChangeListResponseDto> getChanges(String projectId) {
        return portfolioServicePort.getChanges(projectId).map(responseMapper::toResponse);
    }

    @Override
    public Mono<ChangeDetailResponseDto> getChange(String projectId, String changeName) {
        return portfolioServicePort.getChange(projectId, changeName).map(responseMapper::toResponse);
    }

    @Override
    public Mono<ContinuationResponseDto> getContinuation(String projectId, String changeName) {
        return continuationServicePort.getContinuation(projectId, changeName).map(responseMapper::toResponse);
    }
}
