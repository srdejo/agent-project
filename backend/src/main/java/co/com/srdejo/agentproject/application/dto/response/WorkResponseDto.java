package co.com.srdejo.agentproject.application.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** The "Mi trabajo" page: sections {@code continue}, {@code actionable}, {@code decisions}, {@code review}, {@code activity}. */
public record WorkResponseDto(
        @JsonProperty("continue") List<ContinueItemDto> continueItems,
        List<ActionableGroupDto> actionable,
        List<DecisionItemDto> decisions,
        List<ReviewItemDto> review,
        List<ActivityItemDto> activity
) {

    public record ContinueItemDto(String projectId, String projectName, String changeName, int done, int total,
                                  String nextNumber, String nextText, boolean inObjective) {
    }

    public record ActionableGroupDto(String projectId, String projectName, List<ActionableItemDto> items, int more) {
    }

    public record ActionableItemDto(Long taskId, String source, String origin, String changeName, String number,
                                    String text, String status, boolean inObjective) {
    }

    public record DecisionItemDto(String projectId, String projectName, String blockerId, String description,
                                  String owner, String impact, List<String> affects, Integer openTasksAffected) {
    }

    public record ReviewItemDto(String kind, String projectId, String projectName, String changeName,
                                String message) {
    }

    public record ActivityItemDto(Instant at, String kind, String projectId, String text) {
    }
}
