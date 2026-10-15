package co.com.srdejo.agentproject.application.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data freshness and agent connectivity, separately: {@code stale} is about the data, {@code agentConnectivity}
 * only about the agent's heartbeat. Dates are null when no run of that kind exists yet.
 */
public record SyncHealthResponseDto(
        Instant lastAttemptedSync,
        Instant lastSuccessfulSync,
        Instant dataGeneratedAt,
        LastRunDto lastRun,
        Long dataAgeSeconds,
        boolean stale,
        long staleAfterSeconds,
        String agentConnectivity
) {

    public record LastRunDto(Instant startedAt, long durationMs, Integer formatVersion, String generatorVersion,
                             String fileError, List<String> created, List<String> updated, List<String> unchanged,
                             List<String> missing, Map<String, String> rejected, Map<String, String> readErrors,
                             Map<String, List<String>> warnings) {
    }
}
