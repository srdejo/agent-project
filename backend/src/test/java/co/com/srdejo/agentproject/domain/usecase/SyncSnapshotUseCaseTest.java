package co.com.srdejo.agentproject.domain.usecase;

import co.com.srdejo.agentproject.domain.model.Change;
import co.com.srdejo.agentproject.domain.model.ClosedTask;
import co.com.srdejo.agentproject.domain.model.Project;
import co.com.srdejo.agentproject.domain.model.ProjectAggregate;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.RepoCommit;
import co.com.srdejo.agentproject.domain.model.SnapshotBatch;
import co.com.srdejo.agentproject.domain.model.SyncOutcome;
import co.com.srdejo.agentproject.domain.model.Task;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static co.com.srdejo.agentproject.domain.usecase.Fixtures.batch;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.brokenChange;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.change;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.ct;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.project;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.rt;
import static co.com.srdejo.agentproject.domain.usecase.Fixtures.stage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SyncSnapshotUseCaseTest {

    private static final Instant T1 = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-16T12:00:00Z");

    private final InMemoryPorts.Projects projects = new InMemoryPorts.Projects();
    private final InMemoryPorts.Runs runs = new InMemoryPorts.Runs();
    private final InMemoryPorts.Activity activity = new InMemoryPorts.Activity();
    private final SyncSnapshotUseCase useCase =
            new SyncSnapshotUseCase(projects, runs, activity, Clock.fixed(T2.plusMillis(250), ZoneOffset.UTC));

    private static ProjectSnapshot nolost(String hash, boolean secondDone, ProjectSnapshot.Change kids) {
        return project("nolost").hash(hash).objective("Kids", "Etapa 1", "openspec:kids")
                .stage(stage("Etapa 1", "in_progress", rt("A", true), rt("B", secondDone)))
                .stage(stage("Etapa 2", "none", rt("C", false)))
                .change(kids)
                .decision("umbral", "Etapa 2")
                .build();
    }

    private static ProjectSnapshot.Change kids() {
        return change("kids", ct("1.1", "Uno", true), ct("1.2", "Dos", false));
    }

    private ProjectAggregate stored() {
        return projects.aggregates.get("nolost");
    }

    @Test
    void createsAProjectWithDerivedCountersAndAHistoryPoint() {
        StepVerifier.create(useCase.apply(nolost("h1", false, kids()), T1, T1)).expectNext(SyncOutcome.CREATED).verifyComplete();

        Project p = stored().project();
        assertThat(p.objective().done()).isEqualTo(2);
        assertThat(p.objective().total()).isEqualTo(4);
        assertThat(p.roadmapDone()).isEqualTo(1);
        assertThat(p.roadmapTotal()).isEqualTo(3);
        assertThat(p.blockers().get(0).openTasksAffected()).isEqualTo(1);
        assertThat(stored().tasks()).filteredOn(t -> t.text().equals("C")).singleElement()
                .satisfies(t -> assertThat(t.heldByBlocker()).isEqualTo("umbral"));
        assertThat(projects.points).hasSize(1);
    }

    @Test
    void sameContentAndRuleVersionIsANoOp() {
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();

        StepVerifier.create(useCase.apply(nolost("h1", false, kids()), T2, T2)).expectNext(SyncOutcome.UNCHANGED).verifyComplete();

        assertThat(projects.replaceCalls).isEqualTo(1);
        assertThat(projects.points).hasSize(1);
    }

    @Test
    void differentContentReplacesTheProject() {
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();

        StepVerifier.create(useCase.apply(nolost("h2", true, kids()), T2, T2)).expectNext(SyncOutcome.UPDATED).verifyComplete();

        assertThat(stored().project().objective().done()).isEqualTo(3);
        assertThat(stored().tasks()).hasSize(5);
        assertThat(projects.points).hasSize(2);
    }

    @Test
    void taskClosedBetweenSyncsIsRecordedWithTheSnapshotDate() {
        Instant generated = T2.minusSeconds(30);
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();

        useCase.apply(nolost("h2", true, change("kids", ct("1.1", "Uno", true), ct("1.2", "Dos", true))), T2, generated)
                .block();

        assertThat(activity.closed).extracting(ClosedTask::source, ClosedTask::scope, ClosedTask::number,
                        ClosedTask::text, ClosedTask::closedAt)
                .containsExactlyInAnyOrder(
                        tuple(Task.TaskSource.ROADMAP, "Etapa 1", null, "B", generated),
                        tuple(Task.TaskSource.OPENSPEC, "kids", "1.2", "Dos", generated));
    }

    @Test
    void newChangeAlreadyCheckedAndCreatedProjectRecordNothing() {
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();
        assertThat(activity.closed).isEmpty();

        useCase.apply(project("nolost").hash("h2").objective("Kids", "Etapa 1", "openspec:kids")
                .stage(stage("Etapa 1", "in_progress", rt("A", true), rt("B", false)))
                .stage(stage("Etapa 2", "none", rt("C", false)))
                .change(kids()).change(change("nuevo", ct("1.1", "Ya hecha", true)))
                .decision("umbral", "Etapa 2").build(), T2, T2).block();

        assertThat(activity.closed).isEmpty();
    }

    @Test
    void ruleVersionReapplicationRecordsNoClosedTasks() {
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();
        projects.aggregates.put("nolost", new ProjectAggregate(stored().project().withRuleVersion(0), stored().stages(),
                stored().changes(), stored().tasks()));

        useCase.apply(nolost("h1", false, kids()), T2, T2).block();

        assertThat(activity.closed).isEmpty();
    }

    @Test
    void activityReplacesCommitsWithoutTouchingTheProjectOutcome() {
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();
        RepoCommit commit = new RepoCommit("nolost", "abc1234", T1, "feat: algo");
        SnapshotBatch withActivity = new SnapshotBatch(2, "test", Fixtures.GENERATED_AT,
                List.of(nolost("h1", false, kids())), Map.of(), Map.of(), Map.of(), Map.of("nolost", List.of(commit)));

        StepVerifier.create(useCase.applySnapshot(withActivity, T2))
                .assertNext(run -> assertThat(run.unchanged()).containsExactly("nolost"))
                .verifyComplete();
        assertThat(activity.commits).containsEntry("nolost", List.of(commit));

        useCase.applySnapshot(batch(nolost("h1", false, kids())), T2).block();

        assertThat(activity.commits).containsEntry("nolost", List.of(commit));
    }

    @Test
    void ruleVersionChangeForcesReapplication() {
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();
        projects.aggregates.put("nolost", new ProjectAggregate(stored().project().withRuleVersion(0), stored().stages(),
                stored().changes(), stored().tasks()));

        StepVerifier.create(useCase.apply(nolost("h1", false, kids()), T2, T2)).expectNext(SyncOutcome.UPDATED).verifyComplete();
    }

    @Test
    void unreadableChangeKeepsItsLastValidVersion() {
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();

        useCase.apply(nolost("h2", true, brokenChange("kids")), T2, T2).block();

        Change kept = stored().changes().get(0);
        assertThat(kept.parseError()).isEqualTo("ilegible");
        assertThat(kept.parseErrorAt()).isEqualTo(T2);
        assertThat(kept.artifacts().proposal().content()).isEqualTo("# Proposal kids");
        assertThat(stored().tasks()).filteredOn(t -> "kids".equals(t.changeName()))
                .extracting(t -> t.text()).containsExactly("Uno", "Dos");
        assertThat(stored().project().objective().done()).isEqualTo(3);
    }

    @Test
    void unreadableChangeNeverSeenIsStoredEmpty() {
        useCase.apply(nolost("h1", false, brokenChange("kids")), T1, T1).block();

        assertThat(stored().changes()).singleElement().satisfies(c -> {
            assertThat(c.parseError()).isEqualTo("ilegible");
            assertThat(c.artifactIndex()).isEmpty();
        });
        assertThat(stored().tasks()).noneMatch(t -> t.changeName() != null);
    }

    @Test
    void archivedChangeInScopeCountsTowardsTheObjective() {
        ProjectSnapshot hub = project("hub").objective("Pagos", "openspec:pagos")
                .archived("2026-09-01-pagos", ct("1.1", "x", true)).build();

        useCase.apply(hub, T1, T1).block();

        Project p = projects.aggregates.get("hub").project();
        assertThat(p.objective().done()).isEqualTo(1);
        assertThat(p.objective().total()).isEqualTo(1);
        assertThat(p.objective().achieved()).isTrue();
    }

    @Test
    void batchRecordsOutcomesReadErrorsMissingAndDuration() {
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();
        useCase.apply(project("contact").build(), T1, T1).block();
        useCase.apply(project("hotel").build(), T1, T1).block();
        SnapshotBatch batch = new SnapshotBatch(2, "test", Fixtures.GENERATED_AT,
                List.of(nolost("h1", false, kids()), project("visionarios").build()),
                Map.of("bad", "state inválido: NOW"), Map.of("hotel", "ROADMAP.md: permiso denegado"),
                Map.of("nolost", List.of("bloqueo descartado")));

        StepVerifier.create(useCase.applySnapshot(batch, T2)).assertNext(run -> {
            assertThat(run.created()).containsExactly("visionarios");
            assertThat(run.unchanged()).containsExactly("nolost");
            assertThat(run.missing()).containsExactly("contact");
            assertThat(run.rejected()).containsEntry("bad", "state inválido: NOW");
            assertThat(run.readErrors()).containsKey("hotel");
            assertThat(run.warnings()).containsKey("nolost");
            assertThat(run.durationMs()).isEqualTo(250);
            assertThat(run.isSuccessful()).isTrue();
        }).verifyComplete();

        assertThat(projects.aggregates.get("hotel").project().readError()).isEqualTo("ROADMAP.md: permiso denegado");
        assertThat(projects.aggregates.get("contact").project().missingSince()).isEqualTo(T2);
        assertThat(projects.aggregates.get("hotel").project().missingSince()).isNull();
    }

    @Test
    void missingProjectComesBackClean() {
        useCase.apply(nolost("h1", false, kids()), T1, T1).block();
        useCase.applySnapshot(batch(project("otro").build()), T1).block();
        assertThat(stored().project().missingSince()).isEqualTo(T1);

        useCase.applySnapshot(batch(nolost("h1", false, kids())), T2).block();

        assertThat(stored().project().missingSince()).isNull();
    }

    @Test
    void aProjectThatFailsToApplyIsRejectedWithoutStoppingTheRest() {
        projects.failOn.add("nolost");

        StepVerifier.create(useCase.applySnapshot(batch(nolost("h1", false, kids()), project("hotel").build()), T1))
                .assertNext(run -> {
                    assertThat(run.created()).containsExactly("hotel");
                    assertThat(run.rejected()).containsEntry("nolost", "error al aplicar: fallo simulado");
                })
                .verifyComplete();
    }

    @Test
    void fileErrorOnlyRecordsTheRun() {
        StepVerifier.create(useCase.recordFileError("JSON inválido", T2)).assertNext(run -> {
            assertThat(run.fileError()).isEqualTo("JSON inválido");
            assertThat(run.isSuccessful()).isFalse();
        }).verifyComplete();

        assertThat(projects.aggregates).isEmpty();
    }
}
