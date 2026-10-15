package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.api.IWorkServicePort;
import co.com.srdejo.agentproject.domain.model.Change;
import co.com.srdejo.agentproject.domain.model.ProgressPoint;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.SyncRun;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.model.TaskStatus;
import co.com.srdejo.agentproject.domain.model.view.Work;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort.ProjectChange;
import co.com.srdejo.agentproject.domain.spi.ISyncRunPersistencePort;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The "Mi trabajo" page (spec {@code my-work}). Only data that exists: no simulated agent runs. */
public class WorkUseCase implements IWorkServicePort {

    static final int ACTIVITY_LIMIT = 20;
    private static final int PROGRESS_LOOKBACK = 200;

    private final IProjectPersistencePort projects;
    private final ISyncRunPersistencePort runs;

    public WorkUseCase(IProjectPersistencePort projects, ISyncRunPersistencePort runs) {
        this.projects = projects;
        this.runs = runs;
    }

    @Override
    public Mono<Work> getWork() {
        return Mono.zip(projects.findAllProjects().collectList(), projects.findAllTasks().collectList(),
                        projects.findAllChanges().collectList(), runs.findLatest(ACTIVITY_LIMIT).collectList(),
                        projects.findRecentProgress(PROGRESS_LOOKBACK).collectList())
                .map(t -> build(t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5()));
    }

    static Work build(List<Project> all, List<Task> tasks, List<ProjectChange> changes, List<SyncRun> recentRuns,
                      List<ProgressPoint> recentProgress) {
        List<Project> included = all.stream().filter(WorkRules::included).sorted(WorkRules.BY_RANK).toList();
        Map<String, Project> byId = included.stream().collect(Collectors.toMap(Project::id, Function.identity()));
        Map<String, List<Task>> tasksByChange = tasks.stream().filter(t -> t.changeName() != null)
                .collect(Collectors.groupingBy(t -> WorkRules.key(t.projectId(), t.changeName())));
        List<ProjectChange> activeChanges = changes.stream()
                .filter(pc -> !pc.change().archived() && byId.containsKey(pc.projectId()))
                .toList();

        return new Work(
                continueItems(activeChanges, tasksByChange, byId),
                actionable(included, tasks, PortfolioUseCase.archivedKeys(changes)),
                decisions(included),
                review(included, activeChanges, tasksByChange, byId),
                activity(all, recentRuns, recentProgress));
    }

    private static List<Work.ContinueItem> continueItems(List<ProjectChange> active, Map<String, List<Task>> tasksByChange,
                                                         Map<String, Project> byId) {
        List<Work.ContinueItem> items = new ArrayList<>();
        for (ProjectChange pc : active) {
            List<Task> own = tasksByChange.getOrDefault(WorkRules.key(pc.projectId(), pc.change().name()), List.of());
            Task next = own.stream().filter(t -> t.status() == TaskStatus.OPEN).findFirst().orElse(null);
            int done = PortfolioUseCase.done(own);
            if (done == 0 || next == null) {
                continue;
            }
            items.add(new Work.ContinueItem(pc.projectId(), byId.get(pc.projectId()).name(), pc.change().name(), done,
                    PortfolioUseCase.countable(own), next.number(), next.text(), own.stream().anyMatch(Task::inObjective)));
        }
        Comparator<Work.ContinueItem> order = Comparator.comparing((Work.ContinueItem i) -> !i.inObjective())
                .thenComparing(i -> byId.get(i.projectId()), WorkRules.BY_RANK)
                .thenComparing(Work.ContinueItem::changeName);
        return items.stream().sorted(order).toList();
    }

    private static List<Work.ActionableGroup> actionable(List<Project> included, List<Task> tasks, Set<String> archivedKeys) {
        Map<String, List<Task>> byProject = WorkRules.actionableByProject(included, tasks, archivedKeys);
        List<Work.ActionableGroup> groups = new ArrayList<>();
        for (Project p : included) {
            List<Task> own = byProject.get(p.id());
            if (own == null || own.isEmpty()) {
                continue;
            }
            List<Work.ActionableItem> items = own.stream().limit(WorkRules.ACTIONABLE_PER_PROJECT)
                    .map(t -> new Work.ActionableItem(t.id(), t.source().name(),
                            t.source() == Task.TaskSource.OPENSPEC ? t.changeName() : null, t.changeName(), t.number(),
                            t.text(), t.status().name(), t.inObjective()))
                    .toList();
            groups.add(new Work.ActionableGroup(p.id(), p.name(), items,
                    Math.max(0, own.size() - WorkRules.ACTIONABLE_PER_PROJECT)));
        }
        return groups;
    }

    private static List<Work.DecisionItem> decisions(List<Project> included) {
        Comparator<Work.DecisionItem> order = Comparator
                .comparing((Work.DecisionItem d) -> d.openTasksAffected() == null ? -1 : d.openTasksAffected(),
                        Comparator.reverseOrder());
        List<Work.DecisionItem> items = new ArrayList<>();
        for (Project p : included) {
            for (Project.Blocker b : p.blockers()) {
                if ("PRODUCT_DECISION".equals(b.type())) {
                    items.add(new Work.DecisionItem(p.id(), p.name(), b.id(), b.description(), b.owner(), b.impact(),
                            b.affects(), b.openTasksAffected()));
                }
            }
        }
        // Stable sort: equal impact keeps the projects' rank order from {@code included}.
        return items.stream().sorted(order).toList();
    }

    private static List<Work.ReviewItem> review(List<Project> included, List<ProjectChange> active,
                                                Map<String, List<Task>> tasksByChange, Map<String, Project> byId) {
        List<Work.ReviewItem> items = new ArrayList<>();
        for (ProjectChange pc : active) {
            Change c = pc.change();
            String projectName = byId.get(pc.projectId()).name();
            if (c.parseError() != null) {
                items.add(new Work.ReviewItem("CHANGE_PARSE_ERROR", pc.projectId(), projectName, c.name(),
                        "No se pudo leer el cambio en el último sync: " + c.parseError()));
            }
            List<Task> own = tasksByChange.getOrDefault(WorkRules.key(pc.projectId(), c.name()), List.of());
            int countable = PortfolioUseCase.countable(own);
            if (countable > 0 && PortfolioUseCase.done(own) == countable) {
                items.add(new Work.ReviewItem("CHANGE_COMPLETE", pc.projectId(), projectName, c.name(),
                        "Todas las tareas están marcadas: listo para verificar y archivar."));
            }
        }
        for (Project p : included) {
            if (p.objective() == null) {
                items.add(new Work.ReviewItem("NO_OBJECTIVE", p.id(), p.name(), null,
                        "El ROADMAP no declara `## Objetivo actual`: declara un objetivo."));
            } else if (p.objective().achieved()) {
                items.add(new Work.ReviewItem("OBJECTIVE_ACHIEVED", p.id(), p.name(), null,
                        "Objetivo cumplido (" + p.objective().name() + "): declara el siguiente."));
            }
            if (p.readError() != null) {
                items.add(new Work.ReviewItem("READ_ERROR", p.id(), p.name(), null,
                        "El último sync no pudo leer el repo: " + p.readError()));
            }
            if (p.missingSince() != null) {
                items.add(new Work.ReviewItem("MISSING", p.id(), p.name(), null,
                        "No reportado por el sync desde " + p.missingSince()));
            }
        }
        return items;
    }

    private static List<Work.ActivityItem> activity(List<Project> all, List<SyncRun> runs, List<ProgressPoint> progress) {
        Map<String, String> names = all.stream().collect(Collectors.toMap(Project::id, Project::name));
        List<Work.ActivityItem> items = new ArrayList<>();
        for (SyncRun r : runs) {
            items.add(new Work.ActivityItem(r.startedAt(), "SYNC", null, r.fileError() != null
                    ? "Sync fallido: " + r.fileError()
                    : "Sync: %d creados, %d actualizados, %d sin cambios, %d rechazados, %d con error de lectura"
                    .formatted(r.created().size(), r.updated().size(), r.unchanged().size(), r.rejected().size(),
                            r.readErrors().size())));
        }
        Map<String, List<ProgressPoint>> byProject = progress.stream()
                .sorted(Comparator.comparing(ProgressPoint::takenAt))
                .collect(Collectors.groupingBy(ProgressPoint::projectId));
        byProject.forEach((projectId, points) -> {
            for (int i = 1; i < points.size(); i++) {
                ProgressPoint prev = points.get(i - 1);
                ProgressPoint cur = points.get(i);
                if (prev.objectiveName().equals(cur.objectiveName())
                        && (prev.done() != cur.done() || prev.total() != cur.total())) {
                    items.add(new Work.ActivityItem(cur.takenAt(), "PROGRESS", projectId, "%s · %s: %d/%d → %d/%d"
                            .formatted(names.getOrDefault(projectId, projectId), cur.objectiveName(), prev.done(),
                                    prev.total(), cur.done(), cur.total())));
                }
            }
        });
        return items.stream().sorted(Comparator.comparing(Work.ActivityItem::at).reversed()).limit(ACTIVITY_LIMIT)
                .toList();
    }
}
