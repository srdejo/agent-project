package co.com.srdejo.agentproject.application.handler.impl;

import co.com.srdejo.agentproject.application.dto.response.DailyResponseDto;
import co.com.srdejo.agentproject.application.dto.response.SyncHealthResponseDto;
import co.com.srdejo.agentproject.application.dto.response.WorkResponseDto;
import co.com.srdejo.agentproject.application.handler.IWorkHandler;
import co.com.srdejo.agentproject.application.mapper.IWorkResponseMapper;
import co.com.srdejo.agentproject.domain.api.IDailyServicePort;
import co.com.srdejo.agentproject.domain.api.ISyncHealthServicePort;
import co.com.srdejo.agentproject.domain.api.IWorkServicePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class WorkHandler implements IWorkHandler {

    private final IWorkServicePort workServicePort;
    private final ISyncHealthServicePort syncHealthServicePort;
    private final IDailyServicePort dailyServicePort;
    private final IWorkResponseMapper responseMapper;

    @Override
    public Mono<WorkResponseDto> getWork() {
        return workServicePort.getWork().map(responseMapper::toResponse);
    }

    @Override
    public Mono<SyncHealthResponseDto> getSyncHealth() {
        return syncHealthServicePort.health().map(responseMapper::toResponse);
    }

    @Override
    public Mono<DailyResponseDto> getDaily() {
        return dailyServicePort.getDaily().map(responseMapper::toResponse);
    }
}
