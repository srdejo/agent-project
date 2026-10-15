package co.com.srdejo.agentproject.infrastructure.input.inbox;

public class SnapshotValidationException extends RuntimeException {

    public SnapshotValidationException(String message) {
        super(message);
    }

    public SnapshotValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
