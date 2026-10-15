package co.com.srdejo.agentproject.infrastructure.input.rest;

import co.com.srdejo.agentproject.application.dto.response.ChangeDetailResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ChangeListResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ContinuationResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ProjectCardResponseDto;
import co.com.srdejo.agentproject.application.dto.response.ProjectDetailResponseDto;
import co.com.srdejo.agentproject.application.handler.IPortfolioHandler;
import co.com.srdejo.agentproject.infrastructure.exception.NoDataFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Read-only: nothing here writes, runs commands or talks to the agent. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PortfolioRestController {

    private final IPortfolioHandler portfolioHandler;

    @Operation(summary = "Portfolio cards, ordered by priority rank (ARCHIVED only with includeArchived=true)")
    @GetMapping("/portfolio")
    public Flux<ProjectCardResponseDto> getPortfolio(
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
        return portfolioHandler.getPortfolio(includeArchived);
    }

    @Operation(summary = "Project detail: objective, stages and tasks, blockers and objective history")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project found"),
            @ApiResponse(responseCode = "404", description = "Unknown project", content = @Content)
    })
    @GetMapping("/projects/{projectId}")
    public Mono<ProjectDetailResponseDto> getProject(@PathVariable("projectId") String projectId) {
        return portfolioHandler.getProject(projectId)
                .switchIfEmpty(Mono.error(new NoDataFoundException("Proyecto no encontrado: " + projectId)));
    }

    @Operation(summary = "Active OpenSpec changes of a project, without artifact contents")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Changes listed (empty if the repo has no OpenSpec)"),
            @ApiResponse(responseCode = "404", description = "Unknown project", content = @Content)
    })
    @GetMapping("/projects/{projectId}/changes")
    public Mono<ChangeListResponseDto> getChanges(@PathVariable("projectId") String projectId) {
        return portfolioHandler.getChanges(projectId)
                .switchIfEmpty(Mono.error(new NoDataFoundException("Proyecto no encontrado: " + projectId)));
    }

    @Operation(summary = "An OpenSpec change with its artifacts (raw Markdown) and tasks")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Change found"),
            @ApiResponse(responseCode = "404", description = "Unknown project or change", content = @Content)
    })
    @GetMapping("/projects/{projectId}/changes/{changeName}")
    public Mono<ChangeDetailResponseDto> getChange(@PathVariable("projectId") String projectId,
                                                   @PathVariable("changeName") String changeName) {
        return portfolioHandler.getChange(projectId, changeName)
                .switchIfEmpty(Mono.error(new NoDataFoundException(
                        "Cambio no encontrado: " + projectId + "/" + changeName)));
    }

    @Operation(summary = "Instruction to resume a change, ready to copy. Generated from stored data; has no effects")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instruction generated"),
            @ApiResponse(responseCode = "404", description = "Unknown project or change", content = @Content)
    })
    @GetMapping("/projects/{projectId}/changes/{changeName}/continuation")
    public Mono<ContinuationResponseDto> getContinuation(@PathVariable("projectId") String projectId,
                                                         @PathVariable("changeName") String changeName) {
        return portfolioHandler.getContinuation(projectId, changeName)
                .switchIfEmpty(Mono.error(new NoDataFoundException(
                        "Cambio no encontrado: " + projectId + "/" + changeName)));
    }
}
