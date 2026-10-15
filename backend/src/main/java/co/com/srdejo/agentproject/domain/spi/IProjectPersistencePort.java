package co.com.srdejo.agentproject.domain.spi;

import co.com.srdejo.agentproject.domain.model.Change;
import co.com.srdejo.agentproject.domain.model.ProgressPoint;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.ProjectAggregate;
import co.com.srdejo.agentproject.domain.model.Stage;
import co.com.srdejo.agentproject.domain.model.Task;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IProjectPersistencePort {

    /** The full stored state of a project, artifacts included; empty if the project does not exist. */
    Mono<ProjectAggregate> findAggregate(String projectId);

    /** Replaces everything stored for the project in one transaction, and appends {@code point} if not null. */
    Mono<Void> replace(ProjectAggregate aggregate, ProgressPoint point);

    /** Updates only the project row (sync bookkeeping: seen, read error, missing). */
    Mono<Project> saveProject(Project project);

    Mono<Project> findProject(String projectId);

    Flux<Project> findAllProjects();

    Flux<Stage> findStages(String projectId);

    Flux<Task> findTasks(String projectId);

    Flux<Task> findAllTasks();

    /** Changes without their artifacts. */
    Flux<Change> findChanges(String projectId);

    /** Changes of every project without their artifacts, as (projectId, change) pairs. */
    Flux<ProjectChange> findAllChanges();

    /** A change with its artifacts. */
    Mono<Change> findChange(String projectId, String name);

    Flux<ProgressPoint> findProgress(String projectId, String objectiveName);

    Flux<ProgressPoint> findRecentProgress(int limit);

    record ProjectChange(String projectId, Change change) {
    }
}
