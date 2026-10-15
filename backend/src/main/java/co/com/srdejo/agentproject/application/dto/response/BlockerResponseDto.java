package co.com.srdejo.agentproject.application.dto.response;

import java.util.List;

/** {@code openTasksAffected} is null when none of {@code affects} resolved: show "sin datos", never a guess. */
public record BlockerResponseDto(
        String id,
        String type,
        String description,
        String impact,
        List<String> affects,
        String owner,
        String created,
        String resolution,
        Integer openTasksAffected
) {
}
