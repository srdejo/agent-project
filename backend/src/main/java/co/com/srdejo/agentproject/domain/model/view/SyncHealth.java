package co.com.srdejo.agentproject.domain.model.view;

import co.com.srdejo.agentproject.domain.model.SyncRun;

import java.time.Instant;

/**
 * Data freshness and agent connectivity, kept apart on purpose: {@code stale} is about the data,
 * {@code agentConnectivity} only about the agent's heartbeat.
 */
public record SyncHealth(
        Instant lastAttemptedSync,
        Instant lastSuccessfulSync,
        Instant dataGeneratedAt,
        SyncRun lastRun,
        Long dataAgeSeconds,
        boolean stale,
        long staleAfterSeconds,
        String agentConnectivity
) {
}
