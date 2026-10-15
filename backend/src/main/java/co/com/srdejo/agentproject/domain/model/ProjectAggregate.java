package co.com.srdejo.agentproject.domain.model;

import java.util.List;

/** Everything stored for one project; the unit the sync replaces atomically. */
public record ProjectAggregate(Project project, List<Stage> stages, List<Change> changes, List<Task> tasks) {
}
