package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.api.IPortfolioServicePort;
import co.com.srdejo.agentproject.domain.model.Change;
import co.com.srdejo.agentproject.domain.model.ProgressPoint;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.Stage;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.model.TaskStatus;
import co.com.srdejo.agentproject.domain.model.view.ChangeDetail;
import co.com.srdejo.agentproject.domain.model.view.ChangeList;
import co.com.srdejo.agentproject.domain.model.view.ProjectCard;
import co.com.srdejo.agentproject.domain.model.view.ProjectDetail;
import co.com.srdejo.agentproject.domain.model.view.TaskView;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort.ProjectChange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Spec {@code portfolio-view} and the read half of {@code change-continuation}. */
public class PortfolioUseCase implements IPortfolioServicePort {

    private final IProjectPersistencePort projects;

    public PortfolioUseCase(IProjectPersistencePort projects) {
        this.projects = projects;
    }

    @Override
    public Flux<ProjectCard> getPortfolio(boolean includeArchived) {
        return Mono.zip(projects.findAllProjects().collectList(), projects.findAllTasks().collectList(),
                        projects.findAllChanges().collectList())
                .flatMapMany(t -> {
                    List<Project> all = t.getT1();
                    Set<String> archivedKeys = archivedKeys(t.getT3());
                    Map<String, List<Task>> actionable = WorkRules.actionableByProject(all, t.getT2(), archivedKeys);
                    Map<String, Long> activeChanges = t.getT3().stream()
                            .filter(pc -> !pc.change().archived())
                            .collect(Collectors.groupingBy(ProjectChange::projectId, Collectors.counting()));
                    return Flux.fromIterable(all.stream()
                            .filter(p -> includeArchived || !"ARCHIVED".equals(p.state()))
                            .sorted(WorkRules.BY_RANK)
                            .map(p -> card(p, activeChanges.getOrDefault(p.id(), 0L).intValue(),
                                    actionable.containsKey(p.id())))
                            .toList());
                });
    }

    @Override
    public Mono<ProjectDetail> getProject(String projectId) {
        return projects.findProject(projectId).flatMap(project -> Mono.zip(
                        projects.findStages(projectId).collectList(),
                        projects.findTasks(projectId).collectList(),
                        projects.findChanges(projectId).collectList(),
                        project.objective() == null ? Mono.just(List.<ProgressPoint>of())
                                : projects.findProgress(projectId, project.objective().name()).collectList())
                .map(t -> {
                    List<Stage> stages = t.getT1();
                    List<Task> tasks = t.getT2();
                    List<Change> changes = t.getT3();
                    Set<String> archivedKeys = changes.stream().filter(Change::archived)
                            .map(c -> WorkRules.key(projectId, c.name())).collect(Collectors.toSet());
                    boolean actionable = WorkRules.actionableByProject(List.of(project), tasks, archivedKeys)
                            .containsKey(projectId);
                    int activeChanges = (int) changes.stream().filter(c -> !c.archived()).count();
                    Function<Task, TaskView> view = taskViewer(stages);
                    return new ProjectDetail(
                            card(project, activeChanges, actionable),
                            project.sourceCommit(),
                            objectiveDetail(project, stages, changes, tasks, view),
                            project.achievedObjectives(),
                            stages.stream().map(s -> stageView(s, tasks, view)).toList(),
                            project.blockers(),
                            t.getT4().stream().map(p -> new ProjectDetail.HistoryPoint(p.takenAt(), p.done(), p.total()))
                                    .toList(),
                            ProjectDetail.EVIDENCE_NOT_AVAILABLE);
                }));
    }

    @Override
    public Mono<ChangeList> getChanges(String projectId) {
        return projects.findProject(projectId).flatMap(project -> Mono.zip(
                        projects.findChanges(projectId).collectList(), projects.findTasks(projectId).collectList())
                .map(t -> new ChangeList(project.openspecDetected(), project.openspecSpecsCount(),
                        project.openspecArchivedCount(), project.openspecCommands(),
                        t.getT1().stream().map(c -> {
                            List<Task> own = tasksOf(t.getT2(), c.name());
                            return new ChangeList.ChangeSummary(c.name(), c.archived(), c.artifactIndex(), done(own),
                                    countable(own), own.stream().anyMatch(Task::inObjective), c.parseError(),
                                    c.parseErrorAt(), c.lastParsedAt());
                        }).toList())));
    }

    @Override
    public Mono<ChangeDetail> getChange(String projectId, String changeName) {
        return projects.findProject(projectId).flatMap(project -> projects.findChange(projectId, changeName)
                .zipWith(projects.findTasks(projectId).collectList())
                .map(t -> {
                    Change c = t.getT1();
                    List<Task> own = tasksOf(t.getT2(), c.name());
                    Function<Task, TaskView> view = taskViewer(List.of());
                    return new ChangeDetail(project.id(), project.name(), project.repo(), c.name(), c.archived(),
                            changePath(c), c.metadata(), c.artifacts(), own.stream().map(view).toList(), done(own),
                            countable(own), own.stream().anyMatch(Task::inObjective), c.parseError(), c.parseErrorAt(),
                            c.lastParsedAt(), project.openspecCommands(), ProjectDetail.EVIDENCE_NOT_AVAILABLE);
                }));
    }

    static String changePath(Change c) {
        return c.archived() ? "openspec/changes/archive/" + c.name() + "/" : "openspec/changes/" + c.name() + "/";
    }

    static ProjectCard card(Project p, int activeChanges, boolean hasActionable) {
        Project.Objective o = p.objective();
        ProjectCard.ObjectiveSummary objective = o == null ? null : new ProjectCard.ObjectiveSummary(o.name(), o.done(),
                o.total(), ProjectCard.percent(o.done(), o.total()), o.achieved());
        ProjectCard.Counts roadmap = !p.roadmapDetected() ? null : new ProjectCard.Counts(p.roadmapDone(),
                p.roadmapTotal(), ProjectCard.percent(p.roadmapDone(), p.roadmapTotal()));
        Project.Blocker main = p.blockers().stream()
                .max(Comparator.comparing((Project.Blocker b) -> b.openTasksAffected() == null ? -1 : b.openTasksAffected()))
                .orElse(null);
        return new ProjectCard(p.id(), p.name(), p.alias(), p.summary(), p.state(), p.priorityRank(), p.stack(),
                objective, roadmap, activeChanges, p.openspecDetected(), p.blockers().size(), main, p.sourceModifiedAt(),
                p.missingSince(), p.readError(), p.readErrorAt(), hasActionable);
    }

    static Function<Task, TaskView> taskViewer(List<Stage> stages) {
        Map<Integer, String> titles = stages.stream().collect(Collectors.toMap(Stage::ordinal, Stage::title));
        return t -> new TaskView(t.id(), t.source().name(),
                t.source() == Task.TaskSource.ROADMAP ? titles.get(t.stageOrdinal()) : t.changeName(),
                t.number(), t.text(), t.section(), t.line(), t.status().name(), t.blockedReason(), t.inObjective(),
                t.heldByBlocker());
    }

    static List<Task> tasksOf(List<Task> tasks, String changeName) {
        return tasks.stream().filter(t -> changeName.equals(t.changeName())).toList();
    }

    static int done(List<Task> tasks) {
        return (int) tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
    }

    static int countable(List<Task> tasks) {
        return (int) tasks.stream().filter(t -> t.status() != TaskStatus.CANCELLED).count();
    }

    static Set<String> archivedKeys(List<ProjectChange> changes) {
        return changes.stream().filter(pc -> pc.change().archived())
                .map(pc -> WorkRules.key(pc.projectId(), pc.change().name())).collect(Collectors.toSet());
    }

    private static ProjectDetail.ObjectiveDetail objectiveDetail(Project p, List<Stage> stages, List<Change> changes,
                                                                 List<Task> tasks, Function<Task, TaskView> view) {
        Project.Objective o = p.objective();
        if (o == null) {
            return null;
        }
        List<ProjectDetail.TaskGroup> groups = new ArrayList<>();
        for (Stage s : stages) {
            List<Task> own = tasks.stream()
                    .filter(t -> t.inObjective() && Objects.equals(t.stageOrdinal(), s.ordinal())).toList();
            if (!own.isEmpty()) {
                groups.add(new ProjectDetail.TaskGroup("STAGE", s.title(), done(own), countable(own),
                        own.stream().map(view).toList()));
            }
        }
        for (Change c : changes) {
            List<Task> own = tasksOf(tasks, c.name()).stream().filter(Task::inObjective).toList();
            if (!own.isEmpty()) {
                groups.add(new ProjectDetail.TaskGroup("CHANGE", c.name(), done(own), countable(own),
                        own.stream().map(view).toList()));
            }
        }
        return new ProjectDetail.ObjectiveDetail(o.name(), o.criteria(), o.targetDate(), o.scope(), o.done(), o.total(),
                ProjectCard.percent(o.done(), o.total()), o.achieved(), groups);
    }

    private static ProjectDetail.StageView stageView(Stage s, List<Task> tasks, Function<Task, TaskView> view) {
        List<Task> own = tasks.stream().filter(t -> Objects.equals(t.stageOrdinal(), s.ordinal())).toList();
        return new ProjectDetail.StageView(s.key(), s.title(), s.marker(), s.hasCriteria(), s.backlog(), done(own),
                countable(own), own.stream().map(view).toList());
    }
}
