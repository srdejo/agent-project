package co.com.srdejo.agentproject.domain.model.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The plan for the day (spec {@code daily-plan}): what was done since {@code doneSince}, what to work on and what is
 * blocked. {@code focus} is {@code null} when nothing is actionable; {@code text} is the whole daily as plain text,
 * ready to copy.
 */
public record Daily(
        LocalDate date,
        Instant doneSince,
        List<DoneGroup> done,
        Item focus,
        List<Item> alternatives,
        List<Blocker> blockers,
        List<String> commands,
        String continuation,
        boolean stale,
        String text
) {

    /** {@code kind}: CHANGE (an OpenSpec change) or TASK (a ROADMAP task). {@code done}/{@code total} only for changes in progress. */
    public record Item(String projectId, String projectName, String repo, String kind, String changeName,
                       String nextNumber, String nextText, Integer done, Integer total, boolean inObjective) {
    }

    /** Activity of one project in the period; {@code moreCommits} counts the commits left out of {@code commits}. */
    public record DoneGroup(String projectId, String projectName, List<DoneTask> tasks, List<DoneCommit> commits,
                            int moreCommits) {
    }

    /** {@code scope}: the change name, or the stage key or title of a ROADMAP task. */
    public record DoneTask(String scope, String number, String text) {
    }

    public record DoneCommit(String sha, String subject) {
    }

    /**
     * {@code kind}: EDITORIAL (from portfolio.json: {@code type}, {@code owner}) or TASK (a task the repo declares
     * blocked: {@code scope}, {@code number}, {@code taskText}; {@code description} is the reason).
     */
    public record Blocker(String projectId, String projectName, String kind, String type, String description,
                          String owner, String scope, String number, String taskText) {
    }
}
