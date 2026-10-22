package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.model.ClosedTask;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.RepoCommit;
import co.com.srdejo.agentproject.domain.model.Task;
import co.com.srdejo.agentproject.domain.model.view.Daily;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static co.com.srdejo.agentproject.domain.usecase.Fixtures.change;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.ct;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.project;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.rt;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.stage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DailyUseCaseTest {

    private static final Instant SYNCED = Fixtures.GENERATED_AT.toInstant().plusSeconds(60);

    private final InMemoryPorts.Projects projects = new InMemoryPorts.Projects();
    private final InMemoryPorts.Runs runs = new InMemoryPorts.Runs();
    private final InMemoryPorts.Activity activity = new InMemoryPorts.Activity();
    private final InMemoryPorts.Agent agent = new InMemoryPorts.Agent();

    private void seed(ProjectSnapshot... snapshots) {
        new SyncSnapshotUseCase(projects, runs, activity, Clock.fixed(SYNCED, ZoneOffset.UTC))
                .applySnapshot(Fixtures.batch(snapshots), SYNCED).block();
    }

    private DailyUseCase dailyAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        SyncHealthUseCase health = new SyncHealthUseCase(runs, agent, Duration.ofHours(26), clock);
        return new DailyUseCase(new WorkUseCase(projects, runs), new ContinuationUseCase(projects, health), health,
                projects, activity, clock);
    }

    private Daily daily() {
        return dailyAt(SYNCED).getDaily().block();
    }

    @Test
    void focusIsTheChangeInProgressOfTheObjectiveAndTheResultIsDeterministic() {
        seed(project("b").rank(1).change(change("otro", ct("1.1", "hecho", true), ct("1.2", "sigue", false))).build(),
                project("a").rank(2).objective("MVP", "openspec:kids")
                        .change(change("kids", ct("1.1", "uno", true), ct("1.2", "dos", false))).build());

        Daily daily = daily();

        assertThat(daily.date()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(daily.focus()).satisfies(f -> {
            assertThat(f.projectId()).isEqualTo("a");
            assertThat(f.kind()).isEqualTo("CHANGE");
            assertThat(f.changeName()).isEqualTo("kids");
            assertThat(f.nextNumber()).isEqualTo("1.2");
            assertThat(f.done()).isEqualTo(1);
            assertThat(f.total()).isEqualTo(2);
            assertThat(f.inObjective()).isTrue();
        });
        assertThat(daily.commands()).containsExactly("cd a", "git status", "git pull", "claude", "/opsx:apply kids");
        assertThat(daily.continuation()).contains("kids");
        assertThat(daily.text()).contains("Para hoy trabajaré en:", "Bloqueos:",
                "Ejecuta en consola", "Para continuar:", "Proyecto a · kids (1/2) — objetivo");
        assertThat(dailyAt(SYNCED).getDaily().block()).isEqualTo(daily);
    }

    @Test
    void alternativesComeFromOtherProjectsOneEach() {
        seed(project("a").rank(1)
                        .change(change("x", ct("1.1", "uno", true), ct("1.2", "dos", false)))
                        .change(change("y", ct("1.1", "uno", true), ct("1.2", "dos", false))).build(),
                project("b").rank(2).change(change("z", ct("1.1", "uno", true), ct("1.2", "dos", false))).build(),
                project("c").rank(3).stage(stage("Etapa 1", "in_progress", rt("tarea c", false))).build(),
                project("d").rank(4).stage(stage("Etapa 1", "in_progress", rt("tarea d", false))).build());

        Daily daily = daily();

        assertThat(daily.focus().projectId()).isEqualTo("a");
        assertThat(daily.alternatives()).extracting(Daily.Item::projectId).containsExactly("b", "c");
        assertThat(daily.alternatives().get(1).kind()).isEqualTo("TASK");
    }

    @Test
    void roadmapFocusGetsItsOwnInstructionWithStageAndObjectiveCriteria() {
        seed(project("a").objective("Lanzamiento", "Etapa 1")
                .stage(stage("Etapa 1", "in_progress", rt("Configurar dominio", false))).build());

        Daily daily = daily();

        assertThat(daily.focus().kind()).isEqualTo("TASK");
        assertThat(daily.commands()).last().asString().contains("Para continuar");
        assertThat(daily.continuation()).contains("Tarea del ROADMAP: Configurar dominio (Etapa 1 — etapa)",
                "Objetivo actual: Lanzamiento — criterio de cierre: criterio de Lanzamiento", "git status");
    }

    @Test
    void repoWithoutApplyAsksToPasteTheContinuation() {
        seed(project("a").commands("archive")
                .change(change("kids", ct("1.1", "uno", true), ct("1.2", "dos", false))).build());

        assertThat(daily().commands()).doesNotContain("/opsx:apply kids")
                .last().asString().contains("Para continuar");
    }

    @Test
    void nothingActionableLeavesFocusEmptyAndSaysSo() {
        seed(project("a").stage(stage("Etapa 1", "closed", rt("hecha", true))).build());

        Daily daily = daily();

        assertThat(daily.focus()).isNull();
        assertThat(daily.alternatives()).isEmpty();
        assertThat(daily.commands()).isEmpty();
        assertThat(daily.text()).contains("Nada accionable", "Nada bloqueado", "Sin actividad registrada")
                .doesNotContain("Ejecuta en consola");
    }

    @Test
    void staleDataIsFlaggedInTheText() {
        seed(project("a").stage(stage("Etapa 1", "in_progress", rt("tarea", false))).build());

        Daily daily = dailyAt(SYNCED.plus(Duration.ofDays(3))).getDaily().block();

        assertThat(daily.stale()).isTrue();
        assertThat(daily.text()).contains("Datos obsoletos");
        assertThat(daily().stale()).isFalse();
    }

    @Nested
    class Done {

        private static final Instant MONDAY = Instant.parse("2026-10-19T19:00:00Z");
        private static final Instant FRIDAY = Instant.parse("2026-10-16T22:00:00Z");
        private static final Instant WEDNESDAY = Instant.parse("2026-10-14T15:00:00Z");

        private RepoCommit commit(String project, String sha, Instant at) {
            return new RepoCommit(project, sha, at, "commit " + sha);
        }

        @Test
        void mondayShowsFridayAndTodayButNotEarlier() {
            seed(project("a").stage(stage("Etapa 1", "in_progress", rt("tarea", false))).build());
            activity.replaceCommits("a", List.of(commit("a", "lun", MONDAY.minusSeconds(3600)),
                    commit("a", "vie", FRIDAY), commit("a", "mie", WEDNESDAY))).block();

            Daily daily = dailyAt(MONDAY).getDaily().block();

            assertThat(daily.doneSince()).isEqualTo(Instant.parse("2026-10-16T05:00:00Z"));
            assertThat(daily.done()).singleElement().satisfies(g ->
                    assertThat(g.commits()).extracting(Daily.DoneCommit::sha).containsExactly("lun", "vie"));
            assertThat(daily.text()).contains("Hecho (desde el 2026-10-16):", "  vie commit vie")
                    .doesNotContain("commit mie");
        }

        @Test
        void closedTasksAndCommitsAreGroupedByRankWithACapOfEight() {
            seed(project("b").rank(2).stage(stage("Etapa 1", "in_progress", rt("t", false))).build(),
                    project("a").rank(1).stage(stage("Etapa 1", "in_progress", rt("t", false))).build());
            List<RepoCommit> many = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                many.add(commit("a", "c" + i, FRIDAY.minusSeconds(60L * i)));
            }
            activity.replaceCommits("a", many).block();
            activity.replaceCommits("b", List.of(commit("b", "b1", FRIDAY))).block();
            activity.saveClosed(List.of(new ClosedTask(null, "a", Task.TaskSource.OPENSPEC, "mvp", "4.8", "Desplegar",
                    FRIDAY))).block();

            Daily daily = dailyAt(MONDAY).getDaily().block();

            assertThat(daily.done()).extracting(Daily.DoneGroup::projectId).containsExactly("a", "b");
            Daily.DoneGroup a = daily.done().get(0);
            assertThat(a.tasks()).containsExactly(new Daily.DoneTask("mvp", "4.8", "Desplegar"));
            assertThat(a.commits()).hasSize(8);
            assertThat(a.moreCommits()).isEqualTo(2);
            assertThat(daily.text()).contains("  [x] mvp 4.8 Desplegar", "  … y 2 commits más");
        }

        @Test
        void withoutActivityThePeriodIsTodayAndTheTextSaysSo() {
            seed(project("a").stage(stage("Etapa 1", "in_progress", rt("tarea", false))).build());

            Daily daily = dailyAt(MONDAY).getDaily().block();

            assertThat(daily.doneSince()).isEqualTo(Instant.parse("2026-10-19T05:00:00Z"));
            assertThat(daily.done()).isEmpty();
            assertThat(daily.text()).contains("Sin actividad registrada");
        }
    }

    @Nested
    class Blockers {

        @Test
        void editorialBlockersOfEveryActiveOrNextProjectComeFirstByRank() {
            seed(project("foco").rank(1).change(change("kids", ct("1.1", "uno", true), ct("1.2", "dos", false))).build(),
                    project("hub").rank(3).state("NEXT").decision("pagos").build(),
                    project("web").rank(2).decision("logo").build(),
                    project("viejo").rank(4).state("PAUSED").decision("nada").build());

            Daily daily = daily();

            assertThat(daily.focus().projectId()).isEqualTo("foco");
            assertThat(daily.blockers()).extracting(Daily.Blocker::description)
                    .containsExactly("Decidir logo", "Decidir pagos");
            assertThat(daily.blockers()).allMatch(b -> "EDITORIAL".equals(b.kind()));
            assertThat(daily.text()).contains("Proyecto hub (Decisión de producto): Decidir pagos · responsable: Daniel");
        }

        @Test
        void taskDeclaredBlockedIsABlockerAndNeverTheFocus() {
            seed(project("d").rank(1).objective("MVP", "openspec:mvp")
                            .change(change("mvp", ct("4.8", "x", true),
                                    ct("4.9", "Verificar el ciclo. Bloqueada (2026-10-22): no hay asesores. Además falta el CSV.", false)))
                            .stage(stage("Etapa 1", "in_progress", rt("Publicar (bloqueada: falta el dominio)", false)))
                            .decision("precio").build(),
                    project("n").rank(2).change(change("kids", ct("1.1", "uno", true), ct("1.2", "dos", false))).build());

            Daily daily = daily();

            assertThat(daily.focus().projectId()).isEqualTo("n");
            assertThat(daily.alternatives()).isEmpty();
            assertThat(daily.blockers()).extracting(Daily.Blocker::kind, Daily.Blocker::scope, Daily.Blocker::number,
                            Daily.Blocker::description)
                    .containsExactly(tuple("EDITORIAL", null, null, "Decidir precio"),
                            tuple("TASK", "Etapa 1", null, "falta el dominio"),
                            tuple("TASK", "mvp", "4.9", "no hay asesores"));
            assertThat(daily.text()).contains("- Proyecto d · mvp 4.9 (tarea bloqueada): no hay asesores");
        }

        @Test
        void undefinedStagesAreNotListedAsBlockers() {
            seed(project("a").stage(new ProjectSnapshot.Stage("Etapa 2", "Etapa 2", "none", false, false,
                    List.of(rt("sin criterio", false)))).build());

            assertThat(daily().blockers()).isEmpty();
            assertThat(daily().text()).contains("- Nada bloqueado.");
        }
    }

    @Test
    void textHasTheThreeSectionsInOrder() {
        seed(project("a").decision("x").stage(stage("Etapa 1", "in_progress", rt("tarea", false))).build());

        String text = daily().text();

        assertThat(text.indexOf("Hecho")).isLessThan(text.indexOf("Para hoy trabajaré en"));
        assertThat(text.indexOf("Para hoy trabajaré en")).isLessThan(text.indexOf("Bloqueos:"));
        assertThat(text.indexOf("Bloqueos:")).isLessThan(text.indexOf("Ejecuta en consola"));
    }
}
