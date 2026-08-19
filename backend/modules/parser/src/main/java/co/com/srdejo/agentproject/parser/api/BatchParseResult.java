package co.com.srdejo.agentproject.parser.api;

import java.util.Map;

/**
 * Result of parsing a batch file (progreso.json / nuevo.json — see docs/SYNC_PROTOCOL.md). A single
 * malformed entry never fails the whole file: it's reported in {@code rejected} (id -> reason) while
 * the rest of {@code valid} entries can still be applied.
 */
public record BatchParseResult(
        Map<String, SyncPayload> valid,
        Map<String, String> rejected
) {
}
