package co.com.srdejo.agentproject.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code evidence} is NOT_AVAILABLE until verification evidence is recorded (Fase 2). */
public record ProjectDetailResponseDto(
        ProjectCardResponseDto card,
        String sourceCommit,
        ObjectiveDetailDto objective,
        List<AchievedObjectiveDto> achievedObjectives,
        List<StageDto> stages,
        List<BlockerResponseDto> blockers,
        List<HistoryPointDto> history,
        String evidence
) {

    public record ObjectiveDetailDto(String name, String criteria, LocalDate targetDate, List<ScopeRefDto> scope,
                                     int done, int total, Integer percent, boolean achieved,
                                     List<TaskGroupDto> groups) {
    }

    public record ScopeRefDto(String ref, String kind, boolean resolved) {
    }

    public record TaskGroupDto(String kind, String label, int done, int total, List<TaskResponseDto> tasks) {
    }

    public record AchievedObjectiveDto(String date, String name, String criteria) {
    }

    public record StageDto(String key, String title, String marker, boolean hasCriteria, boolean backlog, int done,
                           int total, List<TaskResponseDto> tasks) {
    }

    public record HistoryPointDto(Instant takenAt, int done, int total) {
    }
}
