package co.com.srdejo.agentproject.application.handler;

import co.com.srdejo.agentproject.application.dto.response.SyncHealthResponseDto;
import co.com.srdejo.agentproject.application.dto.response.WorkResponseDto;
import reactor.core.publisher.Mono;

public interface IWorkHandler {

    Mono<WorkResponseDto> getWork();

    Mono<SyncHealthResponseDto> getSyncHealth();
}
