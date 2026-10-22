package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.SyncRun;
import co.com.srdejo.agentproject.domain.model.view.Continuation;
import co.com.srdejo.agentproject.domain.model.view.ProjectCard;
import co.com.srdejo.agentproject.domain.model.view.Work;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static co.com.srdejo.agentproject.domain.usecase.Fixtures.change;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.ct;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.project;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.rt;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.stage;
import static org.assertj.core.api.Assertions.assertThat;

class ReadUseCasesTest {

    private static final Instant GENERATED = Fixtures.GENERATED_AT.toInstant();
    private static final Instant SYNCED = GENERATED.plusSeconds(60);

    private final InMemoryPorts.Projects projects = new InMemoryPorts.Projects();
    private final InMemoryPorts.Runs runs = new InMemoryPorts.Runs();
    private final InMemoryPorts.Activity activity = new InMemoryPorts.Activity();
    private final InMemoryPorts.Agent agent = new InMemoryPorts.Agent();

    private void seed(ProjectSnapshot... snapshots) {
        SyncSnapshotUseCase sync = new SyncSnapshotUseCase(projects, runs, activity, Clock.fixed(SYNCED, ZoneOffset.UTC));
        sync.applySnapshot(Fixtures.batch(snapshots), SYNCED).block();
    }

    private SyncHealthUseCase healthAt(Instant now) {
        return new SyncHealthUseCase(runs, agent, Duration.ofHours(26), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Nested
    class Health {

        @Test
        void withoutRunsEverythingIsNullAndStale() {
            StepVerifier.create(healthAt(SYNCED).health()).assertNext(h -> {
                assertThat(h.lastAttemptedSync()).isNull();
                assertThat(h.lastSuccessfulSync()).isNull();
                assertThat(h.stale()).isTrue();
                assertThat(h.agentConnectivity()).isEqualTo("offline");
            }).verifyComplete();
        }

        @Test
        void failedLastRunKeepsTheEarlierSuccessAndAgesFromItsGeneration() {
            seed(project("hotel").build());
            runs.save(SyncRun.fileError(SYNCED.plusSeconds(3600), 5, "JSON inválido")).block();

            StepVerifier.create(healthAt(GENERATED.plus(Duration.ofHours(25))).health()).assertNext(h -> {
                assertThat(h.lastAttemptedSync()).isEqualTo(SYNCED.plusSeconds(3600));
                assertThat(h.lastSuccessfulSync()).isEqualTo(SYNCED);
                assertThat(h.lastRun().fileError()).isEqualTo("JSON inválido");
                assertThat(h.dataAgeSeconds()).isEqualTo(Duration.ofHours(25).toSeconds());
                assertThat(h.stale()).isFalse();
            }).verifyComplete();
            StepVerifier.create(healthAt(GENERATED.plus(Duration.ofHours(30))).health())
                    .assertNext(h -> assertThat(h.stale()).isTrue()).verifyComplete();
        }

        @Test
        void agentConnectivityOnlyFollowsTheHeartbeatPort() {
            agent.online = true;
            StepVerifier.create(healthAt(SYNCED).health())
                    .assertNext(h -> assertThat(h.agentConnectivity()).isEqualTo("online")).verifyComplete();
        }
    }

    @Nested
    class Portfolio {

        private final PortfolioUseCase portfolio = new PortfolioUseCase(projects);

        @Test
        void cardsAreOrderedByRankExcludeArchivedAndExposeObjectiveOrItsAbsence() {
            seed(project("b").rank(2).objective("Vacío", "Etapa 9").stage(stage("Etapa 1", "closed")).build(),
                    project("a").rank(1).objective("MVP", "Etapa 1").stage(stage("Etapa 1", "in_progress",
                            rt("x", true), rt("y", false))).build(),
                    project("c").build(),
                    project("z").state("ARCHIVED").build());

            StepVerifier.create(portfolio.getPortfolio(false).collectList()).assertNext(cards -> {
                assertThat(cards).extracting(ProjectCard::id).containsExactly("a", "b", "c");
                assertThat(cards.get(0).objective().percent()).isEqualTo(50);
                assertThat(cards.get(0).hasActionable()).isTrue();
                assertThat(cards.get(1).objective().percent()).as("denominador 0 = Sin datos").isNull();
                assertThat(cards.get(2).objective()).as("sin objetivo declarado").isNull();
            }).verifyComplete();
            StepVerifier.create(portfolio.getPortfolio(true).count()).expectNext(4L).verifyComplete();
        }

        @Test
        void detailExposesUnresolvedReferencesGroupsAndEvidenceState() {
            seed(project("a").objective("MVP", "Etapa 1", "Etapa 9", "openspec:kids")
                    .stage(stage("Etapa 1", "in_progress", rt("x", true)))
                    .change(change("kids", ct("1.1", "uno", false))).build());

            StepVerifier.create(portfolio.getProject("a")).assertNext(d -> {
                assertThat(d.objective().scope()).extracting(s -> s.resolved()).containsExactly(true, false, true);
                assertThat(d.objective().groups()).extracting(g -> g.label()).containsExactly("Etapa 1 — etapa", "kids");
                assertThat(d.evidence()).isEqualTo("NOT_AVAILABLE");
                assertThat(d.history()).hasSize(1);
            }).verifyComplete();
        }

        @Test
        void unknownProjectOrChangeCompletesEmpty() {
            seed(project("a").build());

            StepVerifier.create(portfolio.getProject("nope")).verifyComplete();
            StepVerifier.create(portfolio.getChanges("nope")).verifyComplete();
            StepVerifier.create(portfolio.getChange("a", "nope")).verifyComplete();
        }

        @Test
        void changesListThePresentArtifactsWithoutContents() {
            seed(project("a").change(change("kids", ct("1.1", "uno", true), ct("1.2", "dos", false))).build(),
                    project("b").noOpenSpec().build());

            StepVerifier.create(portfolio.getChanges("a")).assertNext(list -> {
                assertThat(list.openspecDetected()).isTrue();
                assertThat(list.changes()).singleElement().satisfies(c -> {
                    assertThat(c.artifacts()).containsExactly("proposal", "tasks", "metadata");
                    assertThat(c.done()).isEqualTo(1);
                    assertThat(c.total()).isEqualTo(2);
                });
            }).verifyComplete();
            StepVerifier.create(portfolio.getChanges("b"))
                    .assertNext(list -> assertThat(list.openspecDetected()).isFalse()).verifyComplete();
            StepVerifier.create(portfolio.getChange("a", "kids"))
                    .assertNext(c -> assertThat(c.artifacts().design()).as("design.md no existe").isNull())
                    .verifyComplete();
        }
    }

    @Nested
    class ContinuationText {

        private Continuation continuation(Instant now) {
            return new ContinuationUseCase(projects, healthAt(now)).getContinuation("a", "kids").block();
        }

        @Test
        void isDeterministicAndSuggestsApplyWithPendingWork() {
            seed(project("a").objective("Kids", "openspec:kids")
                    .change(change("kids", ct("1.1", "uno", true), ct("1.2", "dos", false))).build());

            Continuation first = continuation(SYNCED);
            Continuation second = continuation(SYNCED.plusSeconds(3600));

            assertThat(first.text()).isEqualTo(second.text());
            assertThat(first.suggestedCommand()).isEqualTo("/opsx:apply kids");
            assertThat(first.text()).contains("Objetivo actual: Kids", "- [ ] 1.2 dos", "Siguiente paso: 1.2 dos",
                    "openspec/changes/kids/proposal.md", "git status", "2026-10-15");
            assertThat(projects.replaceCalls).isEqualTo(1);
        }

        @Test
        void withoutApplyInstalledDescribesTheStepInPlainWords() {
            seed(project("a").commands("archive").change(change("kids", ct("1.1", "uno", false))).build());

            Continuation c = continuation(SYNCED);

            assertThat(c.suggestedCommand()).isNull();
            assertThat(c.text()).doesNotContain("/opsx:apply").contains("Continúa con la tarea 1.1");
        }

        @Test
        void allDoneSuggestsArchive() {
            seed(project("a").change(change("kids", ct("1.1", "uno", true))).build());

            assertThat(continuation(SYNCED).suggestedCommand()).isEqualTo("/opsx:archive kids");
        }

        @Test
        void staleDataStartsWithAWarning() {
            seed(project("a").change(change("kids", ct("1.1", "uno", false))).build());

            Continuation c = continuation(GENERATED.plus(Duration.ofDays(3)));

            assertThat(c.stale()).isTrue();
            assertThat(c.text()).startsWith("⚠ ATENCIÓN");
        }

        @Test
        void unknownChangeIsEmpty() {
            seed(project("a").build());

            StepVerifier.create(new ContinuationUseCase(projects, healthAt(SYNCED)).getContinuation("a", "nope"))
                    .verifyComplete();
        }
    }

    @Nested
    class MyWork {

        private Work work() {
            return new WorkUseCase(projects, runs).getWork().block();
        }

        @Test
        void pausedProjectsContributeNothing() {
            seed(project("p").state("PAUSED").stage(stage("Etapa 1", "in_progress", rt("x", false)))
                    .change(change("c", ct("1", "a", true), ct("2", "b", false))).decision("d", "Etapa 1").build());

            Work w = work();

            assertThat(w.continueItems()).isEmpty();
            assertThat(w.actionable()).isEmpty();
            assertThat(w.decisions()).isEmpty();
            assertThat(w.review()).isEmpty();
        }

        @Test
        void continueListsPartialChangesObjectiveFirst() {
            seed(project("a").rank(1).change(change("fuera", ct("1.1", "x", true), ct("1.2", "y", false))).build(),
                    project("b").rank(2).objective("Kids", "openspec:kids")
                            .change(change("kids", ct("1.1", "x", true), ct("1.2", "siguiente", false)))
                            .change(change("nuevo", ct("1.1", "x", false))).build());

            Work w = work();

            assertThat(w.continueItems()).extracting(Work.ContinueItem::changeName).containsExactly("kids", "fuera");
            assertThat(w.continueItems().get(0).nextText()).isEqualTo("siguiente");
            assertThat(w.actionable()).filteredOn(g -> g.projectId().equals("b")).singleElement()
                    .satisfies(g -> assertThat(g.items()).extracting(Work.ActionableItem::changeName).contains("nuevo"));
        }

        @Test
        void actionableSkipsBlockedAndHeldTasksAndCapsPerProject() {
            ProjectSnapshot.Stage big = stage("Etapa 1", "in_progress", rt("1", false), rt("2", false), rt("3", false),
                    rt("4", false), rt("5", false), rt("6", false), rt("7", false));
            seed(project("a").stage(big).stage(new ProjectSnapshot.Stage("Etapa 2", "Etapa 2", "none", false, false,
                            List.of(rt("indefinida", false))))
                    .stage(stage("Fase 3", "closed", rt("pagos", false))).decision("pagos", "Fase 3").build());

            Work.ActionableGroup g = work().actionable().get(0);

            assertThat(g.items()).hasSize(5);
            assertThat(g.more()).isEqualTo(2);
            assertThat(g.items()).extracting(Work.ActionableItem::text).doesNotContain("indefinida", "pagos");
        }

        @Test
        void declaredBlockedTasksAreNotWorkToDo() {
            seed(project("d").objective("MVP", "openspec:mvp")
                    .change(change("mvp", ct("4.8", "x", true), ct("4.9", "Verificar. Bloqueada: faltan asesores", false)))
                    .change(change("otro", ct("1.1", "x", true), ct("1.2", "Bloqueada: falta el CSV", false),
                            ct("1.3", "libre", false)))
                    .stage(stage("Etapa 1", "in_progress", rt("(bloqueada: sin dominio) Publicar", false), rt("ok", false)))
                    .build());

            Work w = work();

            assertThat(w.continueItems()).extracting(Work.ContinueItem::changeName).containsExactly("otro");
            assertThat(w.continueItems().get(0).nextNumber()).isEqualTo("1.3");
            assertThat(w.actionable()).flatExtracting(Work.ActionableGroup::items).extracting(Work.ActionableItem::text)
                    .doesNotContain("Verificar. Bloqueada: faltan asesores", "Bloqueada: falta el CSV",
                            "(bloqueada: sin dominio) Publicar");
        }

        @Test
        void decisionsAreOrderedByOpenTasksAffected() {
            seed(project("a").rank(1).stage(stage("Fase 1", "closed", rt("x", false))).decision("chica", "Fase 1").build(),
                    project("b").rank(2).stage(stage("Fase 3", "closed", rt("1", false), rt("2", false), rt("3", false)))
                            .decision("grande", "Fase 3").decision("vaga", "algo externo").build());

            assertThat(work().decisions()).extracting(Work.DecisionItem::blockerId)
                    .containsExactly("grande", "chica", "vaga");
        }

        @Test
        void reviewFlagsCompleteChangesAchievedAndMissingObjectives() {
            seed(project("v").objective("Login", "openspec:login")
                            .change(change("login", ct("1.1", "x", true))).build(),
                    project("s").build());

            assertThat(work().review()).extracting(Work.ReviewItem::kind)
                    .containsExactlyInAnyOrder("CHANGE_COMPLETE", "OBJECTIVE_ACHIEVED", "NO_OBJECTIVE");
        }

        @Test
        void activityShowsRunsAndObjectiveProgress() {
            seed(project("d").hash("h1").objective("MVP", "Etapa 1")
                    .stage(stage("Etapa 1", "in_progress", rt("x", true), rt("y", false))).build());
            new SyncSnapshotUseCase(projects, runs, activity, Clock.fixed(SYNCED.plusSeconds(60), ZoneOffset.UTC))
                    .applySnapshot(Fixtures.batch(project("d").hash("h2").objective("MVP", "Etapa 1")
                            .stage(stage("Etapa 1", "in_progress", rt("x", true), rt("y", true))).build()),
                            SYNCED.plusSeconds(60))
                    .block();

            assertThat(work().activity()).extracting(Work.ActivityItem::text)
                    .contains("Proyecto d · MVP: 1/2 → 2/2")
                    .anyMatch(t -> t.startsWith("Sync: 0 creados, 1 actualizados"));
        }
    }
}
