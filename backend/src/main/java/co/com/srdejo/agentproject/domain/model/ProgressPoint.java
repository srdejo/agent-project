package co.com.srdejo.agentproject.domain.model;

import java.time.Instant;

public record ProgressPoint(String projectId, String objectiveName, int done, int total, Instant takenAt) {
}
