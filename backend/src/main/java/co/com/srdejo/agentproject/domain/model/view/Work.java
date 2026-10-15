package co.com.srdejo.agentproject.domain.model.view;

import java.time.Instant;
import java.util.List;

/** The "Mi trabajo" page (spec {@code my-work}). */
public record Work(
        List<ContinueItem> continueItems,
        List<ActionableGroup> actionable,
        List<DecisionItem> decisions,
        List<ReviewItem> review,
        List<ActivityItem> activity
) {

    public record ContinueItem(String projectId, String projectName, String changeName, int done, int total,
                               String nextNumber, String nextText, boolean inObjective) {
    }

    /** At most {@code WorkRules.ACTIONABLE_PER_PROJECT} items; {@code more} counts the rest. */
    public record ActionableGroup(String projectId, String projectName, List<ActionableItem> items, int more) {
    }

    public record ActionableItem(Long taskId, String source, String origin, String changeName, String number,
                                 String text, String status, boolean inObjective) {
    }

    /** {@code openTasksAffected} is {@code null} when the decision's {@code affects} could not be resolved. */
    public record DecisionItem(String projectId, String projectName, String blockerId, String description,
                               String owner, String impact, List<String> affects, Integer openTasksAffected) {
    }

    /** {@code kind}: CHANGE_COMPLETE, OBJECTIVE_ACHIEVED, NO_OBJECTIVE, READ_ERROR, MISSING, CHANGE_PARSE_ERROR. */
    public record ReviewItem(String kind, String projectId, String projectName, String changeName, String message) {
    }

    /** {@code kind}: SYNC or PROGRESS. */
    public record ActivityItem(Instant at, String kind, String projectId, String text) {
    }
}
