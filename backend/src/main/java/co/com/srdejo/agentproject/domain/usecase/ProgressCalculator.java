package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.model.TaskStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single progress rule of the Control Center (spec {@code progress-model}): derives each task's status,
 * resolves the objective's scope and the blockers' {@code affects}, and counts objective and roadmap progress.
 * Pure — no Spring, no persistence — so the future public view can reuse it as is.
 */
public final class ProgressCalculator {

    /** Bump whenever a rule here changes: stored projects are re-derived on the next snapshot even if unchanged. */
    public static final int RULE_VERSION = 2;

    public static final String UNDEFINED_REASON = "falta de definición";
    private static final String OPENSPEC_PREFIX = "openspec:";
    private static final Pattern ARCHIVE_DATE_PREFIX = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}-");
    private static final Pattern BLOCKED_MARKER = Pattern.compile(
            "(\\()?\\bbloquead[ao]\\b(?:\\s*\\([^)]*\\))?\\s*:\\s*(.+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    private static final int MAX_REASON = 200;

    private ProgressCalculator() {
    }

    public static Result calculate(Input input) {
        List<List<TaskOut>> stageTasks = new ArrayList<>();
        for (StageIn stage : input.stages()) {
            List<TaskOut> out = new ArrayList<>();
            for (TaskIn task : stage.tasks()) {
                out.add(stage.backlog() ? null : new TaskOut(roadmapStatus(task, stage), reason(task, stage)));
            }
            stageTasks.add(out);
        }
        List<List<TaskOut>> changeTasks = new ArrayList<>();
        for (ChangeIn change : input.changes()) {
            List<TaskOut> out = new ArrayList<>();
            for (TaskIn task : change.tasks()) {
                String declared = openDeclaredBlock(task);
                out.add(new TaskOut(task.cancelled() ? TaskStatus.CANCELLED
                        : task.checked() ? TaskStatus.DONE
                        : declared != null ? TaskStatus.BLOCKED : TaskStatus.OPEN, declared));
            }
            changeTasks.add(out);
        }

        List<ScopeRef> scope = new ArrayList<>();
        Counts objective = new Counts(0, 0);
        if (input.objectiveScope() != null) {
            Set<TaskOut> inScope = new LinkedHashSet<>();
            for (String ref : input.objectiveScope()) {
                Resolution r = resolve(ref, input, stageTasks, changeTasks);
                scope.add(new ScopeRef(ref.trim(), r.kind(), r.resolved()));
                inScope.addAll(r.tasks());
            }
            inScope.forEach(t -> t.inObjective = true);
            objective = count(inScope);
        }

        Map<String, Integer> blockerOpenTasks = new LinkedHashMap<>();
        for (BlockerIn blocker : input.blockers()) {
            Set<TaskOut> affected = new LinkedHashSet<>();
            boolean anyResolved = false;
            for (String ref : blocker.affects()) {
                Resolution r = resolve(ref, input, stageTasks, changeTasks);
                anyResolved |= r.resolved();
                affected.addAll(r.tasks());
            }
            List<TaskOut> open = affected.stream().filter(t -> t.status.isOpen()).toList();
            open.forEach(t -> {
                if (t.heldByBlocker == null) {
                    t.heldByBlocker = blocker.id();
                }
            });
            blockerOpenTasks.put(blocker.id(), anyResolved ? open.size() : null);
        }

        List<TaskOut> roadmapTasks = stageTasks.stream().flatMap(List::stream).filter(t -> t != null).toList();
        Counts roadmap = count(roadmapTasks);
        boolean achieved = objective.total() > 0 && objective.done() == objective.total();

        return new Result(stageTasks, changeTasks, scope, objective, roadmap, achieved, blockerOpenTasks);
    }

    private static TaskStatus roadmapStatus(TaskIn task, StageIn stage) {
        if (task.cancelled()) {
            return TaskStatus.CANCELLED;
        }
        if (task.checked()) {
            return TaskStatus.DONE;
        }
        if (openDeclaredBlock(task) != null || "none".equals(stage.marker()) && !stage.hasCriteria()) {
            return TaskStatus.BLOCKED;
        }
        if ("in_progress".equals(stage.marker())) {
            return TaskStatus.IN_PROGRESS;
        }
        return TaskStatus.OPEN;
    }

    private static String reason(TaskIn task, StageIn stage) {
        if (roadmapStatus(task, stage) != TaskStatus.BLOCKED) {
            return null;
        }
        String declared = openDeclaredBlock(task);
        return declared != null ? declared : UNDEFINED_REASON;
    }

    private static String openDeclaredBlock(TaskIn task) {
        return task.checked() || task.cancelled() ? null : declaredBlockReason(task.text());
    }

    /** The reason of a task the repo declares blocked ("Bloqueada: …", "Bloqueada (fecha): …", "(bloqueada: …)"), or {@code null}. */
    static String declaredBlockReason(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = BLOCKED_MARKER.matcher(text);
        if (!m.find()) {
            return null;
        }
        String reason = m.group(2);
        if (m.group(1) != null && reason.indexOf(')') >= 0) {
            reason = reason.substring(0, reason.indexOf(')'));
        }
        int sentence = reason.indexOf(". ");
        if (sentence >= 0) {
            reason = reason.substring(0, sentence);
        }
        reason = reason.strip();
        if (reason.endsWith(".")) {
            reason = reason.substring(0, reason.length() - 1);
        }
        if (reason.length() > MAX_REASON) {
            reason = reason.substring(0, MAX_REASON - 1).stripTrailing() + "…";
        }
        return reason.isEmpty() ? null : reason;
    }

    private static Counts count(Iterable<TaskOut> tasks) {
        int done = 0;
        int total = 0;
        for (TaskOut t : tasks) {
            if (t.status == TaskStatus.CANCELLED) {
                continue;
            }
            total++;
            if (t.status == TaskStatus.DONE) {
                done++;
            }
        }
        return new Counts(done, total);
    }

    private static Resolution resolve(String rawRef, Input input, List<List<TaskOut>> stageTasks,
                                      List<List<TaskOut>> changeTasks) {
        String ref = rawRef.trim();
        List<TaskOut> tasks = new ArrayList<>();
        if (ref.toLowerCase(Locale.ROOT).startsWith(OPENSPEC_PREFIX)) {
            String name = ref.substring(OPENSPEC_PREFIX.length()).trim();
            boolean found = false;
            for (int i = 0; i < input.changes().size(); i++) {
                if (matchesChange(input.changes().get(i), name)) {
                    found = true;
                    tasks.addAll(changeTasks.get(i));
                }
            }
            return new Resolution(RefKind.CHANGE, found, tasks);
        }
        boolean found = false;
        for (int i = 0; i < input.stages().size(); i++) {
            if (matchesStage(input.stages().get(i), ref)) {
                found = true;
                stageTasks.get(i).stream().filter(t -> t != null).forEach(tasks::add);
            }
        }
        return new Resolution(RefKind.STAGE, found, tasks);
    }

    private static boolean matchesChange(ChangeIn change, String name) {
        if (change.name().equals(name)) {
            return true;
        }
        return change.archived() && ARCHIVE_DATE_PREFIX.matcher(change.name()).replaceFirst("").equals(name);
    }

    static boolean matchesStage(StageIn stage, String ref) {
        if (ref.isEmpty()) {
            return false;
        }
        if (stage.key() != null && stage.key().equalsIgnoreCase(ref)) {
            return true;
        }
        String title = stage.title().toLowerCase(Locale.ROOT);
        String lowerRef = ref.toLowerCase(Locale.ROOT);
        if (!title.startsWith(lowerRef)) {
            return false;
        }
        return title.length() == lowerRef.length() || !Character.isLetterOrDigit(title.charAt(lowerRef.length()));
    }

    public enum RefKind {STAGE, CHANGE}

    /** {@code objectiveScope} is {@code null} when the project declares no current objective. */
    public record Input(List<StageIn> stages, List<ChangeIn> changes, List<String> objectiveScope,
                        List<BlockerIn> blockers) {
    }

    public record StageIn(String key, String title, String marker, boolean hasCriteria, boolean backlog,
                          List<TaskIn> tasks) {
    }

    public record ChangeIn(String name, boolean archived, List<TaskIn> tasks) {
    }

    public record TaskIn(boolean checked, boolean cancelled, String text) {

        public TaskIn(boolean checked, boolean cancelled) {
            this(checked, cancelled, "");
        }
    }

    public record BlockerIn(String id, List<String> affects) {
    }

    public record ScopeRef(String ref, RefKind kind, boolean resolved) {
    }

    public record Counts(int done, int total) {
    }

    /**
     * Mutable on purpose: objective membership and the holding blocker are filled in after statuses are known.
     * Identity-based equality, so the same task reached by two scope refs is counted once.
     */
    public static final class TaskOut {
        private final TaskStatus status;
        private final String blockedReason;
        private boolean inObjective;
        private String heldByBlocker;

        TaskOut(TaskStatus status, String blockedReason) {
            this.status = status;
            this.blockedReason = blockedReason;
        }

        public TaskStatus status() {
            return status;
        }

        public String blockedReason() {
            return blockedReason;
        }

        public boolean inObjective() {
            return inObjective;
        }

        public String heldByBlocker() {
            return heldByBlocker;
        }
    }

    /**
     * Task lists are parallel to the input: a {@code null} entry is a backlog task, which is neither derived nor counted.
     * {@code blockerOpenTasks} maps a blocker id to its open affected tasks, or {@code null} if no {@code affects} resolved.
     */
    public record Result(List<List<TaskOut>> stageTasks, List<List<TaskOut>> changeTasks, List<ScopeRef> scope,
                         Counts objective, Counts roadmap, boolean objectiveAchieved,
                         Map<String, Integer> blockerOpenTasks) {
    }

    private record Resolution(RefKind kind, boolean resolved, List<TaskOut> tasks) {
    }
}
