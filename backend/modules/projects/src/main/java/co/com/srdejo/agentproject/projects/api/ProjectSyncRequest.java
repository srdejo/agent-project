package co.com.srdejo.agentproject.projects.api;

import java.util.List;

/**
 * Contract other modules use to apply an already-validated sync update onto a project.
 * Owned by {@code modules:projects} so this module never depends on {@code modules:parser}'s types.
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
        List<String> completed,
        List<String> nextTasks,
        List<String> blocked,
        List<Check> checks,
        List<Event> events,
        String syncHash
) {

    public record Check(String name, boolean ok, String duration) {
    }

    public record Event(String time, String mark, String text) {
    }
}
