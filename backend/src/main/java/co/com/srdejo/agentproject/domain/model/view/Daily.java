package co.com.srdejo.agentproject.domain.model.view;

import java.time.LocalDate;
import java.util.List;

/**
 * The plan for the day (spec {@code daily-plan}). {@code focus} is {@code null} when nothing is actionable;
 * {@code text} is the whole daily as plain text, ready to copy.
 */
public record Daily(
        LocalDate date,
        Item focus,
        List<Item> alternatives,
        List<Need> needs,
        List<String> commands,
        String continuation,
        boolean stale,
        String text
) {

    /** {@code kind}: CHANGE (an OpenSpec change) or TASK (a ROADMAP task). {@code done}/{@code total} only for changes in progress. */
    public record Item(String projectId, String projectName, String repo, String kind, String changeName,
                       String nextNumber, String nextText, Integer done, Integer total, boolean inObjective) {
    }

    public record Need(String projectId, String projectName, String type, String description, String owner) {
    }
}
