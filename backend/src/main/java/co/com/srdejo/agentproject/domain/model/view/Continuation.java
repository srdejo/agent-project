package co.com.srdejo.agentproject.domain.model.view;

/** {@code suggestedCommand} is {@code null} when the needed {@code opsx} command is not installed in the repo. */
public record Continuation(String text, String suggestedCommand, boolean stale) {
}
