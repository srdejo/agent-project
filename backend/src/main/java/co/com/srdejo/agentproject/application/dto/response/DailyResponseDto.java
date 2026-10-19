package co.com.srdejo.agentproject.application.dto.response;

import java.time.LocalDate;
import java.util.List;

/** The daily: {@code focus} is {@code null} when nothing is actionable; {@code text} is the whole daily, ready to copy. */
public record DailyResponseDto(
        LocalDate date,
        DailyItemDto focus,
        List<DailyItemDto> alternatives,
        List<DailyNeedDto> needs,
        List<String> commands,
        String continuation,
        boolean stale,
        String text
) {

    public record DailyItemDto(String projectId, String projectName, String repo, String kind, String changeName,
                               String nextNumber, String nextText, Integer done, Integer total, boolean inObjective) {
    }

    public record DailyNeedDto(String projectId, String projectName, String type, String description, String owner) {
    }
}
