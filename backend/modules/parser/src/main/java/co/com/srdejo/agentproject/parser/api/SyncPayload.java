package co.com.srdejo.agentproject.parser.api;

import java.time.Instant;
import java.util.List;

/**
 * Validated, normalized sync payload for one project — see docs/SYNC_PROTOCOL.md.
 * Missing optional list fields are normalized to empty lists by {@code SyncPayloadParser}.
 * {@code id} is the project id: for a single-object payload it comes from the JSON body;
 * for a batch entry (progreso.json / nuevo.json) it's the map key.
 *
 * <p>{@code alias}, {@code priority}, {@code priorityRank} and {@code openQuestion} are the
 * optional editorial layer of the portfolio (JSON keys {@code alias}, {@code priority},
 * {@code priority_rank}, {@code open_question}). For those four a {@code null} value means
 * <strong>the field did not come in the JSON</strong>, so the value already stored for the project
 * must be kept; an empty string means the JSON carried the field explicitly as {@code null} (or
 * empty), i.e. clear the stored value. {@code priority}, when present and non-null, is one of
 * {@code NOW | NEXT | DECIDE | ON_TRACK | FROZEN}.</p>
 *
 * <p>{@code priorityRank} is an {@code Integer}, so the empty string is not available as the
 * "clear me" marker: it uses {@code 0} instead, a value outside its own domain since a valid rank
 * is 1 or greater. {@code priority} is the decision <em>group</em> of the project, while
 * {@code priorityRank} is its <em>position</em> in the portfolio ordering (1 = first).</p>
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
        String summary,
        String alias,
        String priority,
        Integer priorityRank,
        String openQuestion,
        List<String> stack,
        List<Task> tasks,
        List<TaskCheck> checks,
        List<AgentEvent> events,
        Instant lastModified
) {

    public record Task(String name, String stage, String status, String date, String commit) {
    }

    public record TaskCheck(String name, boolean ok, String duration) {
    }

    public record AgentEvent(String time, String mark, String text) {
    }
}
