package co.com.srdejo.agentproject.application.handler;

import co.com.srdejo.agentproject.application.dto.response.ChangeDetailResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ChangeListResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ContinuationResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ProjectCardResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ProjectDetailResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Unknown project or change ids complete empty; the REST layer turns that into a 404. */
public interface IPortfolioHandler {

    Flux<ProjectCardResponseDto> getPortfolio(boolean includeArchived);

    Mono<ProjectDetailResponseDto> getProject(String projectId);

    Mono<ChangeListResponseDto> getChanges(String projectId);

    Mono<ChangeDetailResponseDto> getChange(String projectId, String changeName);

    Mono<ContinuationResponseDto> getContinuation(String projectId, String changeName);
}
