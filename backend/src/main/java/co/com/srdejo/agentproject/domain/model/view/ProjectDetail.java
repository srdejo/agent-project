package co.com.srdejo.agentproject.domain.model.view;

import co.com.srdejo.agentproject.domain.model.Project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the project page needs. {@code evidence} stays {@code NOT_AVAILABLE} until verification evidence is
 * recorded (Fase 2): DONE means "completed according to the repo", not independently verified.
 */
public record ProjectDetail(
        ProjectCard card,
        String sourceCommit,
        ObjectiveDetail objective,
        List<Project.AchievedObjective> achievedObjectives,
        List<StageView> stages,
        List<Project.Blocker> blockers,
        List<HistoryPoint> history,
        String evidence
) {

    public static final String EVIDENCE_NOT_AVAILABLE = "NOT_AVAILABLE";

    public record ObjectiveDetail(String name, String criteria, LocalDate targetDate, List<Project.ScopeRef> scope,
                                  int done, int total, Integer percent, boolean achieved, List<TaskGroup> groups) {
    }

    /** Objective tasks grouped by the stage or change they come from. */
    public record TaskGroup(String kind, String label, int done, int total, List<TaskView> tasks) {
    }

    public record StageView(String key, String title, String marker, boolean hasCriteria, boolean backlog, int done,
                            int total, List<TaskView> tasks) {
    }

    public record HistoryPoint(Instant takenAt, int done, int total) {
    }
}
