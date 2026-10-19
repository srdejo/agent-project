package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.view.Daily;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static co.com.srdejo.agentproject.domain.usecase.Fixtures.change;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.ct;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.project;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.rt;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.stage;
import static org.assertj.core.api.Assertions.assertThat;

class DailyUseCaseTest {

    private static final Instant SYNCED = Fixtures.GENERATED_AT.toInstant().plusSeconds(60);

    private final InMemoryPorts.Projects projects = new InMemoryPorts.Projects();
    private final InMemoryPorts.Runs runs = new InMemoryPorts.Runs();
    private final InMemoryPorts.Agent agent = new InMemoryPorts.Agent();

    private void seed(ProjectSnapshot... snapshots) {
        new SyncSnapshotUseCase(projects, runs, Clock.fixed(SYNCED, ZoneOffset.UTC))
                .applySnapshot(Fixtures.batch(snapshots), SYNCED).block();
    }

    private DailyUseCase dailyAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        SyncHealthUseCase health = new SyncHealthUseCase(runs, agent, Duration.ofHours(26), clock);
        return new DailyUseCase(new WorkUseCase(projects, runs), new ContinuationUseCase(projects, health), health,
                projects, clock);
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
        assertThat(daily.text()).contains("Para hoy trabajaré en:", "Para trabajar necesito:",
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
    void needsStartWithTheFocusBlockers() {
        seed(project("a").rank(1).decision("precio")
                        .change(change("kids", ct("1.1", "uno", true), ct("1.2", "dos", false))).build(),
                project("b").rank(2).decision("logo")
                        .stage(stage("Etapa 1", "in_progress", rt("tarea b", false))).build());

        assertThat(daily().needs()).extracting(Daily.Need::description).containsExactly("Decidir precio", "Decidir logo");
    }

    @Test
    void withoutBlockersInTheChosenProjectsNeedsFallBackToPortfolioDecisions() {
        seed(project("a").rank(1).stage(stage("Etapa 1", "in_progress", rt("tarea a", false))).build(),
                project("q").rank(2).noRoadmap().noOpenSpec().decision("contrato").build());

        Daily daily = daily();

        assertThat(daily.needs()).extracting(Daily.Need::projectId).containsExactly("q");
        assertThat(daily.text()).contains("Proyecto q (Decisión de producto): Decidir contrato · responsable: Daniel");
    }

    @Test
    void nothingActionableLeavesFocusEmptyAndSaysSo() {
        seed(project("a").stage(stage("Etapa 1", "closed", rt("hecha", true))).build());

        Daily daily = daily();

        assertThat(daily.focus()).isNull();
        assertThat(daily.alternatives()).isEmpty();
        assertThat(daily.commands()).isEmpty();
        assertThat(daily.text()).contains("Nada accionable", "Nada pendiente de terceros")
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
}
