package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.api.IContinuationServicePort;
import co.com.srdejo.agentproject.domain.api.ISyncHealthServicePort;
import co.com.srdejo.agentproject.domain.model.Change;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.model.TaskStatus;
import co.com.srdejo.agentproject.domain.model.view.Continuation;
import co.com.srdejo.agentproject.domain.model.view.SyncHealth;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.util.List;

/**
 * Builds the instruction to resume an OpenSpec change (spec {@code change-continuation}, design D8). Deterministic:
 * only stored data and the snapshot date go in, never the current time. Reads only.
 */
public class ContinuationUseCase implements IContinuationServicePort {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final IProjectPersistencePort projects;
    private final ISyncHealthServicePort health;

    public ContinuationUseCase(IProjectPersistencePort projects, ISyncHealthServicePort health) {
        this.projects = projects;
        this.health = health;
    }

    @Override
    public Mono<Continuation> getContinuation(String projectId, String changeName) {
        return projects.findProject(projectId).flatMap(project -> Mono.zip(
                        projects.findChange(projectId, changeName),
                        projects.findTasks(projectId).collectList(),
                        health.health())
                .map(t -> build(project, t.getT1(), PortfolioUseCase.tasksOf(t.getT2(), changeName), t.getT3())));
    }

    static Continuation build(Project project, Change change, List<Task> tasks, SyncHealth health) {
        String path = PortfolioUseCase.changePath(change);
        List<Task> pending = tasks.stream().filter(t -> t.status() != TaskStatus.DONE && t.status() != TaskStatus.CANCELLED)
                .toList();
        List<Task> done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).toList();
        boolean inObjective = tasks.stream().anyMatch(Task::inObjective);
        String command = suggestedCommand(project.openspecCommands(), change.name(), pending.isEmpty(), tasks.isEmpty());

        StringBuilder text = new StringBuilder();
        if (health.stale()) {
            text.append("⚠ ATENCIÓN: los datos de este resumen pueden estar desactualizados (el último sync correcto ")
                    .append("supera el umbral de antigüedad). Confirma todo contra el repositorio antes de avanzar.\n\n");
        }
        text.append("Proyecto: ").append(project.name()).append(" (repo `").append(project.repo()).append("`)\n");
        text.append("Cambio OpenSpec: `").append(change.name()).append("`\n");
        if (inObjective && project.objective() != null) {
            text.append("Objetivo actual: ").append(project.objective().name());
            if (project.objective().criteria() != null) {
                text.append(" — criterio de cierre: ").append(project.objective().criteria());
            }
            text.append('\n');
        }
        text.append('\n');
        text.append("Antes de empezar, revisa el estado real del repositorio: `git status`, la rama actual y los ")
                .append("artefactos en `").append(path).append("`. Este resumen sale del sync");
        if (health.dataGeneratedAt() != null) {
            text.append(" generado el ").append(health.dataGeneratedAt().atZone(BOGOTA).toLocalDate());
        }
        text.append(" y puede no reflejar cambios posteriores.\n");
        if (change.parseError() != null) {
            text.append("Aviso: el último sync no pudo leer este cambio (").append(change.parseError())
                    .append("); las tareas de abajo son de la última lectura correcta.\n");
        }

        text.append("\nArtefactos:\n");
        for (String artifact : change.artifactIndex()) {
            text.append("- ").append(path).append(artifactFile(artifact)).append('\n');
        }

        text.append("\nAvance del cambio: ").append(done.size()).append('/')
                .append(PortfolioUseCase.countable(tasks)).append(" tareas completadas según tasks.md.\n");
        if (!done.isEmpty()) {
            text.append("Hechas: ").append(String.join(", ", done.stream().map(ContinuationUseCase::label).toList()))
                    .append('\n');
        }
        if (!pending.isEmpty()) {
            text.append("\nPendientes (en orden):\n");
            pending.forEach(t -> text.append("- [ ] ").append(numbered(t)).append('\n'));
            text.append("\nSiguiente paso: ").append(numbered(pending.get(0))).append('\n');
        }

        text.append('\n');
        if (command != null) {
            text.append("Comando sugerido: ").append(command).append('\n');
        } else if (pending.isEmpty()) {
            text.append("Todas las tareas están marcadas: verifica el cambio y archívalo.\n");
        } else {
            text.append("Continúa con la tarea ").append(label(pending.get(0)))
                    .append(" de tasks.md y márcala solo cuando esté verificada.\n");
        }
        return new Continuation(text.toString(), command, health.stale());
    }

    private static String suggestedCommand(List<String> commands, String change, boolean allDone, boolean noTasks) {
        if (noTasks) {
            return null;
        }
        if (allDone) {
            return commands.contains("archive") ? "/opsx:archive " + change : null;
        }
        return commands.contains("apply") ? "/opsx:apply " + change : null;
    }

    private static String artifactFile(String artifact) {
        return switch (artifact) {
            case "proposal" -> "proposal.md";
            case "design" -> "design.md";
            case "tasks" -> "tasks.md";
            case "metadata" -> ".openspec.yaml";
            default -> artifact;
        };
    }

    private static String label(Task t) {
        return t.number() == null || t.number().isBlank() ? "\"" + t.text() + "\"" : t.number();
    }

    private static String numbered(Task t) {
        return t.number() == null || t.number().isBlank() ? t.text() : t.number() + " " + t.text();
    }
}
