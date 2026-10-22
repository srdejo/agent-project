package co.com.srdejo.agentproject.infrastructure.input.rest;

import co.com.srdejo.agentproject.application.dto.response.DailyResponseDto;
import co.com.srdejo.agentproject.application.dto.response.SyncHealthResponseDto;
import co.com.srdejo.agentproject.application.dto.response.WorkResponseDto;
import co.com.srdejo.agentproject.application.handler.IWorkHandler;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkRestController {

    private final IWorkHandler workHandler;

    @Operation(summary = "\"Mi trabajo\": continue, actionable, decisions, review and activity")
    @GetMapping("/work")
    public Mono<WorkResponseDto> getWork() {
        return workHandler.getWork();
    }

    @Operation(summary = "Sync freshness and agent connectivity, reported separately")
    @GetMapping("/health/sync")
    public Mono<SyncHealthResponseDto> getSyncHealth() {
        return workHandler.getSyncHealth();
    }

    @Operation(summary = "Daily: done since the last active day, focus and alternatives, blockers, console commands and continuation, plus the plain text")
    @GetMapping("/daily")
    public Mono<DailyResponseDto> getDaily() {
        return workHandler.getDaily();
    }
}
