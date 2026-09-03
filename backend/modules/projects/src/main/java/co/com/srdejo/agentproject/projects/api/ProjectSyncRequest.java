package co.com.srdejo.agentproject.projects.api;

import java.time.Instant;
import java.util.List;

/**
 * Contract other modules use to apply an already-validated sync update onto a project.
 * Owned by {@code modules:projects} so this module never depends on {@code modules:parser}'s types.
 *
 * <p>{@code alias}, {@code priority}, {@code priorityRank} and {@code openQuestion} carry the
 * optional editorial layer: {@code null} means "absent from the sync JSON" (keep the stored value),
 * an empty string means "explicitly cleared". {@code priorityRank} is an {@code Integer}, so its
 * "explicitly cleared" marker is {@code 0} instead of the empty string — a valid rank is 1 or
 * greater, so {@code 0} can never be a real value.</p>
 */
public record ProjectSyncRequest(
        String id,
        String name,
        String repo,
        String stage,
        String status,
        int progress,
        String updatedLabel,
        String commitSha,
        String verifyStatus,
        String summary,
        String alias,
        String priority,
        Integer priorityRank,
        String openQuestion,
        List<String> stack,
        List<Task> tasks,
        List<Check> checks,
        List<Event> events,
        Instant lastModified
) {

    public record Task(String name, String stage, String status, String date, String commit) {
    }

    public record Check(String name, boolean ok, String duration) {
    }

    public record Event(String time, String mark, String text) {
    }
}
