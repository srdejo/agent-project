package co.com.srdejo.agentproject.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The daily: what was done since {@code doneSince}, what to work on ({@code focus} is {@code null} when nothing is
 * actionable) and what is blocked; {@code text} is the whole daily, ready to copy.
 */
public record DailyResponseDto(
        LocalDate date,
        Instant doneSince,
        List<DailyDoneGroupDto> done,
        DailyItemDto focus,
        List<DailyItemDto> alternatives,
        List<DailyBlockerDto> blockers,
        List<String> commands,
        String continuation,
        boolean stale,
        String text
) {

    public record DailyItemDto(String projectId, String projectName, String repo, String kind, String changeName,
                               String nextNumber, String nextText, Integer done, Integer total, boolean inObjective) {
    }

    public record DailyDoneGroupDto(String projectId, String projectName, List<DailyDoneTaskDto> tasks,
                                    List<DailyDoneCommitDto> commits, int moreCommits) {
    }

    public record DailyDoneTaskDto(String scope, String number, String text) {
    }

    public record DailyDoneCommitDto(String sha, String subject) {
    }

    /** {@code kind}: EDITORIAL or TASK; for TASK, {@code description} is the reason the repo gives. */
    public record DailyBlockerDto(String projectId, String projectName, String kind, String type, String description,
                                  String owner, String scope, String number, String taskText) {
    }
}
