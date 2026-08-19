package co.com.srdejo.agentproject.parser.api;

public class SyncValidationException extends RuntimeException {

    public SyncValidationException(String message) {
        super(message);
    }

    public SyncValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
