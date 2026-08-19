package co.com.srdejo.agentproject.projects.web;

import java.util.List;

public record ProjectListResponse(List<ProjectSummaryResponse> projects, Stats stats, String lastSync) {

    public record Stats(int count, int avg, int blocked, int verified) {
    }
}
