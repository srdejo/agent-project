package co.com.srdejo.agentproject.parser.api;

import java.util.List;

/**
 * Validated, normalized sync payload for one project — see docs/SYNC_PROTOCOL.md.
 * Missing optional list fields are normalized to empty lists by {@code SyncPayloadParser}.
 */
public record SyncPayload(
        String id,
        String name,
        String repo,
        int progress,
        String stage,
        String status,
        String updated,
        String commit,
        String verify,
        List<String> completed,
        List<String> next,
        List<String> blocked,
        List<TaskCheck> checks,
        List<AgentEvent> events
) {

    public record TaskCheck(String name, boolean ok, String duration) {
    }

    public record AgentEvent(String time, String mark, String text) {
    }
}
