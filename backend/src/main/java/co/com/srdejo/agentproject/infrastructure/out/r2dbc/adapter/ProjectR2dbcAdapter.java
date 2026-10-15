package co.com.srdejo.agentproject.infrastructure.out.r2dbc.adapter;

import co.com.srdejo.agentproject.domain.model.Change;
import co.com.srdejo.agentproject.domain.model.ProgressPoint;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.ProjectAggregate;
import co.com.srdejo.agentproject.domain.model.Stage;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.OpenSpecChangeEntity;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.RoadmapStageEntity;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.entity.TaskEntity;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.mapper.R2dbcEntityMapper;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IObjectiveProgressPointRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IOpenSpecChangeRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IProjectRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.IRoadmapStageRepository;
import co.com.srdejo.agentproject.infrastructure.out.r2dbc.repository.ITaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stores a project as rows of project, roadmap_stage, openspec_change and task. Tasks reference their stage and
 * change by database id here, and by stage ordinal / change name in the domain.
 */
@RequiredArgsConstructor
public class ProjectR2dbcAdapter implements IProjectPersistencePort {

    private final IProjectRepository projectRepository;
    private final IRoadmapStageRepository stageRepository;
    private final IOpenSpecChangeRepository changeRepository;
    private final ITaskRepository taskRepository;
    private final IObjectiveProgressPointRepository pointRepository;
    private final R2dbcEntityMapper mapper;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<ProjectAggregate> findAggregate(String projectId) {
        return projectRepository.findById(projectId).flatMap(project -> Mono.zip(
                        stageRepository.findByProjectIdOrderByOrdinal(projectId).collectList(),
                        changeRepository.findByProjectIdOrderByOrdinal(projectId).collectList(),
                        taskRepository.findByProjectIdOrderByOrdinal(projectId).collectList())
                .map(t -> new ProjectAggregate(mapper.toProject(project),
                        t.getT1().stream().map(mapper::toStage).toList(),
                        t.getT2().stream().map(mapper::toChange).toList(),
                        toTasks(t.getT3(), t.getT1(), t.getT2()))));
    }

    @Override
    public Mono<Void> replace(ProjectAggregate aggregate, ProgressPoint point) {
        Project project = aggregate.project();
        String id = project.id();
        Mono<Void> work = projectRepository.existsById(id)
                .flatMap(exists -> projectRepository.save(mapper.toEntity(project, !exists)))
                .then(taskRepository.deleteAllOfProject(id))
                .then(stageRepository.deleteAllOfProject(id))
                .then(changeRepository.deleteAllOfProject(id))
                .then(Mono.zip(
                        stageRepository.saveAll(aggregate.stages().stream().map(s -> mapper.toEntity(id, s)).toList())
                                .collectMap(RoadmapStageEntity::getOrdinal, RoadmapStageEntity::getId),
                        changeRepository.saveAll(aggregate.changes().stream().map(c -> mapper.toEntity(id, c)).toList())
                                .collectMap(OpenSpecChangeEntity::getName, OpenSpecChangeEntity::getId)))
                .flatMapMany(ids -> taskRepository.saveAll(aggregate.tasks().stream()
                        .map(t -> mapper.toEntity(t,
                                t.stageOrdinal() == null ? null : ids.getT1().get(t.stageOrdinal()),
                                t.changeName() == null ? null : ids.getT2().get(t.changeName())))
                        .toList()))
                .then(point == null ? Mono.empty() : pointRepository.save(mapper.toEntity(point)).then());
        return transactionalOperator.transactional(work);
    }

    @Override
    public Mono<Project> saveProject(Project project) {
        return projectRepository.save(mapper.toEntity(project, false)).map(mapper::toProject);
    }

    @Override
    public Mono<Project> findProject(String projectId) {
        return projectRepository.findById(projectId).map(mapper::toProject);
    }

    @Override
    public Flux<Project> findAllProjects() {
        return projectRepository.findAll().map(mapper::toProject);
    }

    @Override
    public Flux<Stage> findStages(String projectId) {
        return stageRepository.findByProjectIdOrderByOrdinal(projectId).map(mapper::toStage);
    }

    @Override
    public Flux<Task> findTasks(String projectId) {
        return Mono.zip(stageRepository.findByProjectIdOrderByOrdinal(projectId).collectList(),
                        changeRepository.findSummariesOfProject(projectId).collectList(),
                        taskRepository.findByProjectIdOrderByOrdinal(projectId).collectList())
                .flatMapIterable(t -> toTasks(t.getT3(), t.getT1(), t.getT2()));
    }

    @Override
    public Flux<Task> findAllTasks() {
        return Mono.zip(stageRepository.findAll().collectList(), changeRepository.findAllSummaries().collectList(),
                        taskRepository.findAllByOrderByProjectIdAscOrdinalAsc().collectList())
                .flatMapIterable(t -> toTasks(t.getT3(), t.getT1(), t.getT2()));
    }

    @Override
    public Flux<Change> findChanges(String projectId) {
        return changeRepository.findSummariesOfProject(projectId).map(mapper::toChange);
    }

    @Override
    public Flux<ProjectChange> findAllChanges() {
        return changeRepository.findAllSummaries().map(e -> new ProjectChange(e.getProjectId(), mapper.toChange(e)));
    }

    @Override
    public Mono<Change> findChange(String projectId, String name) {
        return changeRepository.findByProjectIdAndName(projectId, name).map(mapper::toChange);
    }

    @Override
    public Flux<ProgressPoint> findProgress(String projectId, String objectiveName) {
        return pointRepository.findByProjectIdAndObjectiveNameOrderByTakenAt(projectId, objectiveName)
                .map(mapper::toPoint);
    }

    @Override
    public Flux<ProgressPoint> findRecentProgress(int limit) {
        return pointRepository.findRecent(limit).map(mapper::toPoint);
    }

    private List<Task> toTasks(List<TaskEntity> tasks, List<RoadmapStageEntity> stages,
                               List<OpenSpecChangeEntity> changes) {
        Map<Long, Integer> stageOrdinals = stages.stream()
                .collect(Collectors.toMap(RoadmapStageEntity::getId, RoadmapStageEntity::getOrdinal));
        Map<Long, String> changeNames = changes.stream()
                .collect(Collectors.toMap(OpenSpecChangeEntity::getId, OpenSpecChangeEntity::getName));
        return tasks.stream()
                .map(t -> mapper.toTask(t, t.getStageId() == null ? null : stageOrdinals.get(t.getStageId()),
                        t.getChangeId() == null ? null : changeNames.get(t.getChangeId())))
                .toList();
    }
}
