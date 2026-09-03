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
        String summary,
        String alias,
        String priority,
        Integer priorityRank,
        String openQuestion,
        List<Integer> series,
        int tasksDone,
        int tasksTotal,
        List<Event> events
) {

    public record Event(String time, String mark, String text) {
    }
}
