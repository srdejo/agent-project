package co.com.srdejo.agentproject.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * A v2 snapshot whose file-level shape was valid. Entries were judged one by one: {@code rejected} failed
 * validation, {@code readErrors} were reported unreadable by the generator, {@code warnings} lists parts
 * dropped from otherwise valid entries. {@code activity} is the optional block of recent commits per project,
 * {@code null} when the file has none.
 */
public record SnapshotBatch(
        int formatVersion,
        String generatorVersion,
        OffsetDateTime generatedAt,
        List<ProjectSnapshot> valid,
        Map<String, String> rejected,
        Map<String, String> readErrors,
        Map<String, List<String>> warnings,
        Map<String, List<RepoCommit>> activity
) {

    public SnapshotBatch(int formatVersion, String generatorVersion, OffsetDateTime generatedAt,
                         List<ProjectSnapshot> valid, Map<String, String> rejected, Map<String, String> readErrors,
                         Map<String, List<String>> warnings) {
        this(formatVersion, generatorVersion, generatedAt, valid, rejected, readErrors, warnings, null);
    }
}
