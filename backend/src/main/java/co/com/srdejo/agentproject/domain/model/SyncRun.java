package co.com.srdejo.agentproject.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SyncRun(
        Instant startedAt,
        long durationMs,
        Integer formatVersion,
        String generatorVersion,
        Instant generatedAt,
        String fileError,
        List<String> created,
        List<String> updated,
        List<String> unchanged,
        List<String> missing,
        Map<String, String> rejected,
        Map<String, String> readErrors,
        Map<String, List<String>> warnings
) {

    public static SyncRun fileError(Instant startedAt, long durationMs, String error) {
        return new SyncRun(startedAt, durationMs, null, null, null, error, List.of(), List.of(), List.of(), List.of(),
                Map.of(), Map.of(), Map.of());
    }

    /** A run counts as successful when the file was valid and at least one project was applied or confirmed. */
    public boolean isSuccessful() {
        return fileError == null && !(created.isEmpty() && updated.isEmpty() && unchanged.isEmpty());
    }
}
