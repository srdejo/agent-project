package co.com.srdejo.agentproject.domain.model;

/** A ROADMAP stage. {@code ordinal} is its position in the file and how tasks point at it. */
public record Stage(int ordinal, String key, String title, String marker, boolean hasCriteria, boolean backlog) {
}
