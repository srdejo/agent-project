package co.com.srdejo.agentproject.projects.web;

public record BlockedTaskResponse(String projectId, String projectName, String taskName, String stage, String date) {
}
