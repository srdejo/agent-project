package co.com.srdejo.agentproject.domain.model;

import java.time.Instant;

/** A commit reported in the snapshot's {@code activity} block. */
public record RepoCommit(String projectId, String sha, Instant committedAt, String subject) {
}
