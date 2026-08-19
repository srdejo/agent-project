package co.com.srdejo.agentproject.projects.web;

import java.util.List;

public record ProjectDetailResponse(
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
        List<Check> checks,
        List<Event> events,
        List<Snapshot> history
) {

    public record Check(String name, boolean ok, String duration) {
    }

    public record Event(String time, String mark, String text) {
    }

    public record Snapshot(String takenAt, int progress) {
    }
}
