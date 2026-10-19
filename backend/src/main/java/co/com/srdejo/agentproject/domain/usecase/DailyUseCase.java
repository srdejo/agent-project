package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.api.IContinuationServicePort;
import co.com.srdejo.agentproject.domain.api.IDailyServicePort;
import co.com.srdejo.agentproject.domain.api.ISyncHealthServicePort;
import co.com.srdejo.agentproject.domain.api.IWorkServicePort;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.Stage;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.model.view.Continuation;
import co.com.srdejo.agentproject.domain.model.view.Daily;
import co.com.srdejo.agentproject.domain.model.view.Work;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The daily (spec {@code daily-plan}): a view over "Mi trabajo", so its priorities are exactly the ones Mi trabajo
 * uses. Deterministic: only stored data and today's date go in.
 */
public class DailyUseCase implements IDailyServicePort {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final int ALTERNATIVES = 2;
    private static final int PORTFOLIO_DECISIONS = 2;
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
    private final Clock clock;

    public DailyUseCase(IWorkServicePort work, IContinuationServicePort continuations, ISyncHealthServicePort health,
                        IProjectPersistencePort projects, Clock clock) {
        this.work = work;
        this.continuations = continuations;
        this.health = health;
        this.projects = projects;
        this.clock = clock;
    }

    @Override
    public Mono<Daily> getDaily() {
        LocalDate today = LocalDate.now(clock.withZone(BOGOTA));
        return Mono.zip(work.getWork(), health.health()).flatMap(t -> {
            Work w = t.getT1();
            boolean stale = t.getT2().stale();
            List<Candidate> chosen = choose(w);
            if (chosen.isEmpty()) {
                List<Daily.Need> needs = portfolioDecisions(w);
                return Mono.just(new Daily(today, null, List.of(), needs, List.of(), null, stale,
                        text(today, null, List.of(), needs, List.of(), null, stale)));
            }
            return Flux.fromIterable(chosen)
                    .concatMap(c -> projects.findProject(c.projectId()))
                    .collectMap(Project::id, Function.identity())
                    .flatMap(byId -> {
                        List<Daily.Item> items = chosen.stream().map(c -> c.toItem(byId.get(c.projectId()))).toList();
                        Daily.Item focus = items.get(0);
                        List<Daily.Need> needs = needs(items, byId, w);
                        Project focusProject = byId.get(focus.projectId());
                        List<String> commands = commands(focus, focusProject);
                        return continuation(focus, chosen.get(0).taskId(), focusProject).map(cont -> new Daily(today, focus,
                                items.subList(1, items.size()), needs, commands, cont, stale,
                                text(today, focus, items.subList(1, items.size()), needs, commands, cont, stale)));
                    });
        });
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

    private static List<Daily.Need> needs(List<Daily.Item> items, Map<String, Project> byId, Work w) {
        List<Daily.Need> needs = new ArrayList<>();
        for (Daily.Item item : items) {
            Project p = byId.get(item.projectId());
            if (p == null) {
                continue;
            }
            p.blockers().forEach(b -> needs.add(new Daily.Need(p.id(), p.name(), b.type(), b.description(), b.owner())));
        }
        return needs.isEmpty() ? portfolioDecisions(w) : needs;
    }

    private static List<Daily.Need> portfolioDecisions(Work w) {
        return w.decisions().stream().limit(PORTFOLIO_DECISIONS)
                .map(d -> new Daily.Need(d.projectId(), d.projectName(), "PRODUCT_DECISION", d.description(), d.owner()))
                .toList();
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

    static String text(LocalDate today, Daily.Item focus, List<Daily.Item> alternatives, List<Daily.Need> needs,
                       List<String> commands, String continuation, boolean stale) {
        StringBuilder t = new StringBuilder();
        t.append("Daily — ").append(today).append("\n\n");
        if (stale) {
            t.append("⚠ Datos obsoletos: el último sync correcto supera el umbral. Confirma en los repos antes de ")
                    .append("seguir.\n\n");
        }
        t.append("Para hoy trabajaré en:\n");
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
        t.append("\nPara trabajar necesito:\n");
        if (needs.isEmpty()) {
            t.append("- Nada pendiente de terceros.\n");
        }
        needs.forEach(n -> {
            t.append("- ").append(n.projectName()).append(" (").append(BLOCKER_LABEL.getOrDefault(n.type(), n.type()))
                    .append("): ").append(n.description());
            if (n.owner() != null) {
                t.append(" · responsable: ").append(n.owner());
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

    private record Candidate(String projectId, String projectName, String kind, Long taskId, String changeName,
                             String nextNumber,
                             String nextText, Integer done, Integer total, boolean inObjective) {

        Daily.Item toItem(Project p) {
            return new Daily.Item(projectId, projectName, p == null ? projectId : p.repo(), kind, changeName, nextNumber,
                    nextText, done, total, inObjective);
        }
    }
}
