package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.api.IContinuationServicePort;
import co.com.srdejo.agentproject.domain.api.IDailyServicePort;
import co.com.srdejo.agentproject.domain.api.ISyncHealthServicePort;
import co.com.srdejo.agentproject.domain.api.IWorkServicePort;
import co.com.srdejo.agentproject.domain.model.ClosedTask;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.RepoCommit;
import co.com.srdejo.agentproject.domain.model.Stage;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.model.TaskStatus;
import co.com.srdejo.agentproject.domain.model.view.Continuation;
import co.com.srdejo.agentproject.domain.model.view.Daily;
import co.com.srdejo.agentproject.domain.model.view.Work;
import co.com.srdejo.agentproject.domain.spi.IActivityPersistencePort;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The daily (spec {@code daily-plan}): what was done, a view over "Mi trabajo" for what to do (so its priorities are
 * exactly the ones Mi trabajo uses) and what is blocked. Deterministic: only stored data and today's date go in.
 */
public class DailyUseCase implements IDailyServicePort {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final int ALTERNATIVES = 2;
    private static final int ACTIVITY_DAYS = 7;
    private static final int COMMITS_PER_PROJECT = 8;
    private static final int TASK_TEXT = 100;
    private static final Set<String> BLOCKER_STATES = Set.of("ACTIVE", "NEXT", "BLOCKED");
    private static final Map<String, String> BLOCKER_LABEL = Map.of(
            "PRODUCT_DECISION", "Decisión de producto",
            "EXTERNAL_DEPENDENCY", "Dependencia externa",
            "TECHNICAL", "Técnico",
            "ENVIRONMENT", "Entorno",
            "SECURITY", "Seguridad",
            "UNKNOWN", "Sin clasificar");

    private final IWorkServicePort work;
    private final IContinuationServicePort continuations;
    private final ISyncHealthServicePort health;
    private final IProjectPersistencePort projects;
    private final IActivityPersistencePort activity;
    private final Clock clock;

    public DailyUseCase(IWorkServicePort work, IContinuationServicePort continuations, ISyncHealthServicePort health,
                        IProjectPersistencePort projects, IActivityPersistencePort activity, Clock clock) {
        this.work = work;
        this.continuations = continuations;
        this.health = health;
        this.projects = projects;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    public Mono<Daily> getDaily() {
        LocalDate today = LocalDate.now(clock.withZone(BOGOTA));
        Instant weekStart = today.minusDays(ACTIVITY_DAYS).atStartOfDay(BOGOTA).toInstant();
        Mono<List<Project>> all = projects.findAllProjects().sort(WorkRules.BY_RANK).collectList();
        return Mono.zip(work.getWork(), health.health(), all,
                        activity.findCommitsSince(weekStart).collectList(),
                        activity.findClosedSince(weekStart).collectList(),
                        blockedTasks())
                .flatMap(t -> {
                    Work w = t.getT1();
                    boolean stale = t.getT2().stale();
                    List<Project> ranked = t.getT3();
                    Instant since = doneSince(today, t.getT4(), t.getT5());
                    List<Daily.DoneGroup> done = done(ranked, since, t.getT4(), t.getT5());
                    List<Daily.Blocker> blockers = blockers(ranked, t.getT6());
                    List<Candidate> chosen = choose(w);
                    if (chosen.isEmpty()) {
                        return Mono.just(new Daily(today, since, done, null, List.of(), blockers, List.of(), null,
                                stale, text(today, since, done, null, List.of(), blockers, List.of(), null, stale)));
                    }
                    Map<String, Project> byId = ranked.stream().collect(Collectors.toMap(Project::id, Function.identity()));
                    List<Daily.Item> items = chosen.stream().map(c -> c.toItem(byId.get(c.projectId()))).toList();
                    Daily.Item focus = items.get(0);
                    List<Daily.Item> alternatives = items.subList(1, items.size());
                    Project focusProject = byId.get(focus.projectId());
                    List<String> commands = commands(focus, focusProject);
                    return continuation(focus, chosen.get(0).taskId(), focusProject).map(cont -> new Daily(today,
                            since, done, focus, alternatives, blockers, commands, cont, stale,
                            text(today, since, done, focus, alternatives, blockers, commands, cont, stale)));
                });
    }

    /** 00:00 of the last day before today with activity in the last week; today's 00:00 if there was none. */
    static Instant doneSince(LocalDate today, List<RepoCommit> commits, List<ClosedTask> closed) {
        LocalDate day = Stream.concat(commits.stream().map(RepoCommit::committedAt),
                        closed.stream().map(ClosedTask::closedAt))
                .map(i -> LocalDate.ofInstant(i, BOGOTA))
                .filter(d -> d.isBefore(today))
                .max(LocalDate::compareTo)
                .orElse(today);
        return day.atStartOfDay(BOGOTA).toInstant();
    }

    private static List<Daily.DoneGroup> done(List<Project> ranked, Instant since, List<RepoCommit> commits,
                                              List<ClosedTask> closed) {
        List<Daily.DoneGroup> groups = new ArrayList<>();
        for (Project p : ranked) {
            List<Daily.DoneTask> tasks = closed.stream()
                    .filter(c -> c.projectId().equals(p.id()) && !c.closedAt().isBefore(since))
                    .map(c -> new Daily.DoneTask(c.scope(), c.number(), c.text()))
                    .toList();
            List<RepoCommit> own = commits.stream()
                    .filter(c -> c.projectId().equals(p.id()) && !c.committedAt().isBefore(since))
                    .toList();
            if (tasks.isEmpty() && own.isEmpty()) {
                continue;
            }
            List<Daily.DoneCommit> shown = own.stream().limit(COMMITS_PER_PROJECT)
                    .map(c -> new Daily.DoneCommit(c.sha(), c.subject())).toList();
            groups.add(new Daily.DoneGroup(p.id(), p.name(), tasks, shown, own.size() - shown.size()));
        }
        return groups;
    }

    /** Tasks the repo declares blocked, outside archived changes, paired with their scope label. */
    private Mono<List<ScopedTask>> blockedTasks() {
        Mono<Set<String>> archived = projects.findAllChanges().collectList().map(PortfolioUseCase::archivedKeys);
        Mono<List<Task>> declared = projects.findAllTasks()
                .filter(t -> t.status() == TaskStatus.BLOCKED && t.blockedReason() != null
                        && !ProgressCalculator.UNDEFINED_REASON.equals(t.blockedReason()))
                .collectList();
        return Mono.zip(archived, declared).flatMap(t -> {
            List<Task> tasks = t.getT2().stream()
                    .filter(task -> task.changeName() == null
                            || !t.getT1().contains(WorkRules.key(task.projectId(), task.changeName())))
                    .toList();
            Set<String> withRoadmap = tasks.stream().filter(task -> task.changeName() == null).map(Task::projectId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return Flux.fromIterable(withRoadmap)
                    .concatMap(id -> projects.findStages(id).collectList().map(s -> Map.entry(id, s)))
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                    .map(stages -> tasks.stream().map(task -> new ScopedTask(task, scope(task, stages))).toList());
        });
    }

    private static String scope(Task task, Map<String, List<Stage>> stages) {
        if (task.changeName() != null) {
            return task.changeName();
        }
        return stages.getOrDefault(task.projectId(), List.of()).stream()
                .filter(s -> Objects.equals(s.ordinal(), task.stageOrdinal()))
                .findFirst()
                .map(s -> s.key() != null ? s.key() : s.title())
                .orElse("ROADMAP");
    }

    private static List<Daily.Blocker> blockers(List<Project> ranked, List<ScopedTask> blocked) {
        List<Project> relevant = ranked.stream().filter(p -> BLOCKER_STATES.contains(p.state())).toList();
        List<Daily.Blocker> result = new ArrayList<>();
        for (Project p : relevant) {
            p.blockers().forEach(b -> result.add(new Daily.Blocker(p.id(), p.name(), "EDITORIAL", b.type(),
                    b.description(), b.owner(), null, null, null)));
        }
        for (Project p : relevant) {
            blocked.stream().filter(s -> s.task().projectId().equals(p.id())).forEach(s -> result.add(
                    new Daily.Blocker(p.id(), p.name(), "TASK", null, s.task().blockedReason(), null, s.scope(),
                            s.task().number(), shorten(s.task().text()))));
        }
        return result;
    }

    private static String shorten(String text) {
        return text.length() <= TASK_TEXT ? text : text.substring(0, TASK_TEXT - 1).stripTrailing() + "…";
    }

    /** Continue items first, then the first actionable task of each project not yet chosen; one item per project. */
    private static List<Candidate> choose(Work w) {
        List<Candidate> candidates = new ArrayList<>();
        w.continueItems().forEach(c -> candidates.add(new Candidate(c.projectId(), c.projectName(), "CHANGE",
                null, c.changeName(), c.nextNumber(), c.nextText(), c.done(), c.total(), c.inObjective())));
        w.actionable().forEach(g -> g.items().stream().findFirst().ifPresent(i -> candidates.add(new Candidate(
                g.projectId(), g.projectName(), i.changeName() != null ? "CHANGE" : "TASK", i.taskId(), i.changeName(),
                i.number(), i.text(), null, null, i.inObjective()))));
        Set<String> projectsSeen = new LinkedHashSet<>();
        List<Candidate> chosen = new ArrayList<>();
        for (Candidate c : candidates) {
            if (chosen.size() == 1 + ALTERNATIVES) {
                break;
            }
            if (projectsSeen.add(c.projectId())) {
                chosen.add(c);
            }
        }
        return chosen;
    }

    private static List<String> commands(Daily.Item focus, Project project) {
        List<String> commands = new ArrayList<>(List.of("cd " + focus.repo(), "git status", "git pull", "claude"));
        boolean canApply = "CHANGE".equals(focus.kind()) && project != null && project.openspecCommands().contains("apply");
        commands.add(canApply ? "/opsx:apply " + focus.changeName() : "(pega la instrucción de \"Para continuar\")");
        return commands;
    }

    private Mono<String> continuation(Daily.Item focus, Long taskId, Project project) {
        if ("CHANGE".equals(focus.kind())) {
            return continuations.getContinuation(focus.projectId(), focus.changeName()).map(Continuation::text)
                    .defaultIfEmpty("");
        }
        return Mono.zip(projects.findStages(focus.projectId()).collectList(),
                        projects.findTasks(focus.projectId()).collectList())
                .map(t -> roadmapContinuation(focus, taskId, project, t.getT1(), t.getT2()));
    }

    static String roadmapContinuation(Daily.Item focus, Long taskId, Project project, List<Stage> stages,
                                      List<Task> tasks) {
        String stage = tasks.stream()
                .filter(t -> Objects.equals(t.id(), taskId))
                .findFirst()
                .flatMap(t -> stages.stream().filter(s -> Objects.equals(s.ordinal(), t.stageOrdinal())).findFirst())
                .map(Stage::title)
                .orElse(null);
        StringBuilder text = new StringBuilder();
        text.append("Proyecto: ").append(focus.projectName()).append(" (repo `").append(focus.repo()).append("`)\n");
        text.append("Tarea del ROADMAP: ").append(focus.nextText());
        if (stage != null) {
            text.append(" (").append(stage).append(')');
        }
        text.append('\n');
        if (focus.inObjective() && project != null && project.objective() != null) {
            text.append("Objetivo actual: ").append(project.objective().name());
            if (project.objective().criteria() != null) {
                text.append(" — criterio de cierre: ").append(project.objective().criteria());
            }
            text.append('\n');
        }
        text.append("\nAntes de empezar, revisa el estado real del repositorio (`git status`, la rama actual) y lee ")
                .append("docs/ROADMAP.md. Marca la tarea [x] solo cuando esté verificada (compila, corre o pasan ")
                .append("sus tests).\n");
        return text.toString();
    }

    static String text(LocalDate today, Instant since, List<Daily.DoneGroup> done, Daily.Item focus,
                       List<Daily.Item> alternatives, List<Daily.Blocker> blockers, List<String> commands,
                       String continuation, boolean stale) {
        StringBuilder t = new StringBuilder();
        t.append("Daily — ").append(today).append("\n\n");
        if (stale) {
            t.append("⚠ Datos obsoletos: el último sync correcto supera el umbral. Confirma en los repos antes de ")
                    .append("seguir.\n\n");
        }
        t.append("Hecho (desde el ").append(LocalDate.ofInstant(since, BOGOTA)).append("):\n");
        if (done.isEmpty()) {
            t.append("- Sin actividad registrada.\n");
        }
        done.forEach(g -> {
            t.append("- ").append(g.projectName()).append(":\n");
            g.tasks().forEach(task -> t.append("  [x] ").append(task.scope()).append(' ')
                    .append(task.number() == null ? "" : task.number() + " ").append(task.text()).append('\n'));
            g.commits().forEach(c -> t.append("  ").append(c.sha()).append(' ').append(c.subject()).append('\n'));
            if (g.moreCommits() > 0) {
                t.append("  … y ").append(g.moreCommits()).append(" commits más\n");
            }
        });
        t.append("\nPara hoy trabajaré en:\n");
        if (focus == null) {
            t.append("- Nada accionable: todo está hecho, bloqueado o en proyectos pausados.\n");
        } else {
            t.append("1. ").append(label(focus)).append('\n');
            t.append("   Siguiente: ").append(next(focus)).append('\n');
            if (!alternatives.isEmpty()) {
                t.append("Alternativas, si el foco está bloqueado:\n");
                alternatives.forEach(a -> t.append("- ").append(label(a)).append(": ").append(next(a)).append('\n'));
            }
        }
        t.append("\nBloqueos:\n");
        if (blockers.isEmpty()) {
            t.append("- Nada bloqueado.\n");
        }
        blockers.forEach(b -> {
            if ("TASK".equals(b.kind())) {
                t.append("- ").append(b.projectName()).append(" · ").append(b.scope())
                        .append(b.number() == null ? "" : " " + b.number()).append(" (tarea bloqueada): ")
                        .append(b.description()).append('\n');
                return;
            }
            t.append("- ").append(b.projectName()).append(" (").append(BLOCKER_LABEL.getOrDefault(b.type(), b.type()))
                    .append("): ").append(b.description());
            if (b.owner() != null) {
                t.append(" · responsable: ").append(b.owner());
            }
            t.append('\n');
        });
        if (!commands.isEmpty()) {
            t.append("\nEjecuta en consola (desde la raíz del workspace):\n");
            commands.forEach(c -> t.append("  ").append(c).append('\n'));
        }
        if (continuation != null && !continuation.isBlank()) {
            t.append("\nPara continuar:\n").append(continuation.stripTrailing()).append('\n');
        }
        return t.toString();
    }

    private static String label(Daily.Item i) {
        String base = i.projectName() + ("CHANGE".equals(i.kind()) ? " · " + i.changeName() : " · ROADMAP");
        if (i.done() != null) {
            base += " (" + i.done() + "/" + i.total() + ")";
        }
        return i.inObjective() ? base + " — objetivo" : base;
    }

    private static String next(Daily.Item i) {
        return i.nextNumber() == null || i.nextNumber().isBlank() ? i.nextText() : i.nextNumber() + " " + i.nextText();
    }

    private record ScopedTask(Task task, String scope) {
    }

    private record Candidate(String projectId, String projectName, String kind, Long taskId, String changeName,
                             String nextNumber,
                             String nextText, Integer done, Integer total, boolean inObjective) {

        Daily.Item toItem(Project p) {
            return new Daily.Item(projectId, projectName, p == null ? projectId : p.repo(), kind, changeName, nextNumber,
                    nextText, done, total, inObjective);
        }
    }
}
