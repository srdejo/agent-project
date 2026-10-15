package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.model.TaskStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Rules shared by "Mi trabajo" and the portfolio (spec {@code my-work}). Pure. */
final class WorkRules {

    static final int ACTIONABLE_PER_PROJECT = 5;
    private static final Set<String> EXCLUDED_STATES = Set.of("PAUSED", "COMPLETED", "ARCHIVED");

    static final Comparator<Project> BY_RANK = Comparator
            .comparing((Project p) -> p.priorityRank() == null ? Integer.MAX_VALUE : p.priorityRank())
            .thenComparing(Project::name, String.CASE_INSENSITIVE_ORDER);

    private WorkRules() {
    }

    static boolean included(Project p) {
        return !EXCLUDED_STATES.contains(p.state());
    }

    /**
     * Open or in-progress tasks not held by a blocker, of included projects, excluding archived changes. Per project:
     * objective tasks first, then IN_PROGRESS before OPEN, then file order.
     */
    static Map<String, List<Task>> actionableByProject(List<Project> projects, List<Task> tasks,
                                                       Set<String> archivedChangeKeys) {
        Set<String> includedIds = projects.stream().filter(WorkRules::included).map(Project::id)
                .collect(Collectors.toSet());
        return tasks.stream()
                .filter(t -> includedIds.contains(t.projectId()))
                .filter(t -> t.status() == TaskStatus.OPEN || t.status() == TaskStatus.IN_PROGRESS)
                .filter(t -> t.heldByBlocker() == null)
                .filter(t -> t.changeName() == null || !archivedChangeKeys.contains(key(t.projectId(), t.changeName())))
                .sorted(Comparator.comparing((Task t) -> !t.inObjective())
                        .thenComparing(t -> t.status() != TaskStatus.IN_PROGRESS)
                        .thenComparing(Task::ordinal))
                .collect(Collectors.groupingBy(Task::projectId, Collectors.toList()));
    }

    static String key(String projectId, String changeName) {
        return projectId + "/" + changeName;
    }
}
