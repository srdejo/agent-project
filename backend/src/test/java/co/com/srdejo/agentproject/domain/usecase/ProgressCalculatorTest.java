package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.BlockerIn;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.ChangeIn;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.Counts;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.Input;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.RefKind;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.Result;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.ScopeRef;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.StageIn;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.TaskIn;
import co.com.srdejo.agentproject.domain.usecase.ProgressCalculator.TaskOut;
import co.com.srdejo.agentproject.domain.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressCalculatorTest {

    private static List<TaskIn> tasks(int done, int open) {
        List<TaskIn> list = new ArrayList<>();
        IntStream.range(0, done).forEach(i -> list.add(new TaskIn(true, false)));
        IntStream.range(0, open).forEach(i -> list.add(new TaskIn(false, false)));
        return list;
    }

    private static StageIn stage(String key, String marker, List<TaskIn> tasks) {
        return new StageIn(key, key + " — algo", marker, true, false, tasks);
    }

    private static Input input(List<StageIn> stages, List<ChangeIn> changes, List<String> scope) {
        return new Input(stages, changes, scope, List.of());
    }

    @Test
    void objectiveWithStagesAndChangeCountsOnlyItsScope() {
        Result r = ProgressCalculator.calculate(input(
                List.of(stage("Etapa 1", "closed", tasks(3, 0)), stage("Etapa 2", "in_progress", tasks(1, 2)),
                        stage("Etapa 3", "none", tasks(0, 4))),
                List.of(new ChangeIn("mvp", false, tasks(2, 1))),
                List.of("Etapa 1", "Etapa 2", "openspec:mvp")));

        assertThat(r.objective()).isEqualTo(new Counts(6, 9));
        assertThat(r.roadmap()).isEqualTo(new Counts(4, 10));
        assertThat(r.scope()).extracting(ScopeRef::kind)
                .containsExactly(RefKind.STAGE, RefKind.STAGE, RefKind.CHANGE);
        assertThat(r.stageTasks().get(2)).noneMatch(TaskOut::inObjective);
    }

    @Test
    void mixedObjectiveMatchesSpecExample() {
        List<TaskIn> roadmap = new ArrayList<>(tasks(12, 7));
        roadmap.add(new TaskIn(false, true));
        Result r = ProgressCalculator.calculate(input(
                List.of(stage("Etapa 1", "in_progress", roadmap)),
                List.of(new ChangeIn("kids", false, tasks(9, 8))),
                List.of("Etapa 1", "openspec:kids")));

        assertThat(r.objective()).isEqualTo(new Counts(21, 36));
        assertThat(Math.round(100.0 * r.objective().done() / r.objective().total())).isEqualTo(58);
    }

    @Test
    void unresolvedReferenceIsFlaggedAndCountsNothing() {
        Result r = ProgressCalculator.calculate(input(
                List.of(stage("Etapa 1", "closed", tasks(2, 0))), List.of(), List.of("Etapa 1", "Etapa 9")));

        assertThat(r.scope()).extracting(ScopeRef::resolved).containsExactly(true, false);
        assertThat(r.objective()).isEqualTo(new Counts(2, 2));
    }

    @Test
    void archivedChangeInScopeCountsItsTasksAsDone() {
        Result r = ProgressCalculator.calculate(input(List.of(),
                List.of(new ChangeIn("2026-09-01-pagos", true, tasks(10, 0))), List.of("openspec:pagos")));

        assertThat(r.scope().get(0).resolved()).isTrue();
        assertThat(r.objective()).isEqualTo(new Counts(10, 10));
    }

    @Test
    void stageWithoutMarkerNorCriteriaBlocksItsOpenTasks() {
        StageIn undefined = new StageIn("Fase 3", "Fase 3 — Pagos", "none", false, false, tasks(1, 1));
        Result r = ProgressCalculator.calculate(input(List.of(undefined), List.of(), null));

        TaskOut open = r.stageTasks().get(0).get(1);
        assertThat(open.status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(open.blockedReason()).isEqualTo(ProgressCalculator.UNDEFINED_REASON);
        assertThat(r.stageTasks().get(0).get(0).status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void stageStatusesFollowTheMarker() {
        Result r = ProgressCalculator.calculate(input(List.of(
                stage("Etapa 1", "in_progress", tasks(0, 1)), stage("Etapa 2", "closed", tasks(0, 1)),
                new StageIn("Etapa 3", "Etapa 3", "none", true, false, tasks(0, 1))), List.of(), null));

        assertThat(r.stageTasks()).extracting(l -> l.get(0).status())
                .containsExactly(TaskStatus.IN_PROGRESS, TaskStatus.OPEN, TaskStatus.OPEN);
    }

    @Test
    void cancelledTaskLeavesTheDenominator() {
        Result r = ProgressCalculator.calculate(input(
                List.of(stage("Etapa 1", "in_progress", List.of(new TaskIn(true, false), new TaskIn(false, true)))),
                List.of(), List.of("Etapa 1")));

        assertThat(r.stageTasks().get(0).get(1).status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(r.objective()).isEqualTo(new Counts(1, 1));
        assertThat(r.objectiveAchieved()).isTrue();
    }

    @Test
    void withoutObjectiveOnlyTheRoadmapIsCounted() {
        Result r = ProgressCalculator.calculate(input(List.of(stage("Etapa 1", "closed", tasks(42, 23))), List.of(), null));

        assertThat(r.scope()).isEmpty();
        assertThat(r.objective()).isEqualTo(new Counts(0, 0));
        assertThat(r.objectiveAchieved()).isFalse();
        assertThat(r.roadmap()).isEqualTo(new Counts(42, 65));
    }

    @Test
    void emptyScopeIsNotAchieved() {
        Result r = ProgressCalculator.calculate(input(List.of(), List.of(), List.of("Etapa 1")));

        assertThat(r.objective()).isEqualTo(new Counts(0, 0));
        assertThat(r.objectiveAchieved()).isFalse();
    }

    @Test
    void taskReachedTwiceCountsOnce() {
        Result r = ProgressCalculator.calculate(input(
                List.of(stage("Etapa 1", "closed", tasks(1, 1))), List.of(), List.of("Etapa 1", "etapa 1")));

        assertThat(r.objective()).isEqualTo(new Counts(1, 2));
    }

    @Test
    void stageReferenceDoesNotMatchALongerNumber() {
        StageIn ten = new StageIn(null, "Etapa 10 — Extra", "closed", true, false, tasks(5, 0));
        StageIn one = new StageIn(null, "Etapa 1 — Base", "closed", true, false, tasks(1, 0));
        Result r = ProgressCalculator.calculate(input(List.of(ten, one), List.of(), List.of("Etapa 1")));

        assertThat(r.objective()).isEqualTo(new Counts(1, 1));
    }

    @Test
    void backlogStageIsNeitherDerivedNorCounted() {
        StageIn backlog = new StageIn(null, "Mejoras futuras", "none", false, true, tasks(0, 3));
        Result r = ProgressCalculator.calculate(input(List.of(stage("Etapa 1", "closed", tasks(1, 0)), backlog),
                List.of(), null));

        assertThat(r.stageTasks().get(1)).containsOnlyNulls();
        assertThat(r.roadmap()).isEqualTo(new Counts(1, 1));
    }

    @Test
    void blockerCountsOpenTasksOfResolvedAffectsAndHoldsThem() {
        Input in = new Input(
                List.of(stage("Fase 3", "in_progress", tasks(2, 6)), stage("Fase 4", "closed", tasks(0, 1))),
                List.of(),
                null,
                List.of(new BlockerIn("pagos", List.of("Fase 3")), new BlockerIn("vago", List.of("algo externo"))));

        Result r = ProgressCalculator.calculate(in);

        assertThat(r.blockerOpenTasks()).containsEntry("pagos", 6).containsEntry("vago", null);
        assertThat(r.stageTasks().get(0)).filteredOn(t -> t.status() != TaskStatus.DONE)
                .allMatch(t -> "pagos".equals(t.heldByBlocker()));
        assertThat(r.stageTasks().get(1).get(0).heldByBlocker()).isNull();
    }
}
