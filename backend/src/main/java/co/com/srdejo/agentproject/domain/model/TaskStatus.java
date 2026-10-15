package co.com.srdejo.agentproject.domain.model;

public enum TaskStatus {
    OPEN, IN_PROGRESS, BLOCKED, DONE, CANCELLED;

    public boolean isOpen() {
        return this == OPEN || this == IN_PROGRESS || this == BLOCKED;
    }
}
