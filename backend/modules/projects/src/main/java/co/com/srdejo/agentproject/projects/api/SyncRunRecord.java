package co.com.srdejo.agentproject.projects.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SyncRunRecord(
        List<String> createdIds,
        List<String> updatedIds,
        List<String> unchangedIds,
        Map<String, String> rejected,
        Instant ranAt
) {
}
