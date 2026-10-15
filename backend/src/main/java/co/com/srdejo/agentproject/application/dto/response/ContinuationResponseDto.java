package co.com.srdejo.agentproject.application.dto.response;

/** {@code suggestedCommand} is null when the needed opsx command is not installed in the repo. */
public record ContinuationResponseDto(String text, String suggestedCommand, boolean stale) {
}
