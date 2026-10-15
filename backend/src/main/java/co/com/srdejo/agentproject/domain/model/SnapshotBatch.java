package co.com.srdejo.agentproject.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * A v2 snapshot whose file-level shape was valid. Entries were judged one by one: {@code rejected} failed
 * validation, {@code readErrors} were reported unreadable by the generator, {@code warnings} lists parts
 * dropped from otherwise valid entries.
 */
public record SnapshotBatch(
        int formatVersion,
        String generatorVersion,
        OffsetDateTime generatedAt,
        List<ProjectSnapshot> valid,
        Map<String, String> rejected,
        Map<String, String> readErrors,
        Map<String, List<String>> warnings
) {
}
