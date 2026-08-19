package co.com.srdejo.agentproject.projects.web;

import java.util.List;

public record ProjectSummaryResponse(
        String id,
        String name,
        String repo,
        int progress,
        String stage,
        String status,
        String updated,
        List<Integer> series,
        List<Event> events
) {

    public record Event(String time, String mark, String text) {
    }
}
