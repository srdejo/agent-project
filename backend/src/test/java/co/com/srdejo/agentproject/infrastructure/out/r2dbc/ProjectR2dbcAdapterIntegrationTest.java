package co.com.srdejo.agentproject.infrastructure.out.r2dbc;

import co.com.srdejo.agentproject.domain.api.IPortfolioServicePort;
import co.com.srdejo.agentproject.domain.api.ISyncServicePort;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.SnapshotBatch;
import co.com.srdejo.agentproject.domain.model.SyncRun;
import co.com.srdejo.agentproject.domain.spi.IProjectPersistencePort;
import co.com.srdejo.agentproject.infrastructure.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The sync use case over the real R2DBC adapter and Postgres: transactions, JSONB and id remapping. */
class ProjectR2dbcAdapterIntegrationTest extends IntegrationTestSupport {

    private static final Instant T1 = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-16T12:00:00Z");

    @Autowired
    private ISyncServicePort sync;

    @Autowired
    private IProjectPersistencePort projects;

    @Autowired
    private IPortfolioServicePort portfolio;

    private static String nolost(boolean secondChecked, String kidsOverride) {
        String kids = kidsOverride != null ? kidsOverride : """
                {"name": "kids", "metadata": "schema: spec-driven",
                 "artifacts": {"proposal": {"content": "# P", "truncated": false}},
                 "tasks": [{"number": "1.1", "text": "Uno", "section": "1. A", "line": 3, "checked": true},
                           {"number": "1.2", "text": "Dos", "section": "1. A", "line": 4, "checked": false}]}""";
        return """
                {"name": "NoLost", "repo": "nolost", "state": "ACTIVE", "priority_rank": 2,
                 "blockers": [{"id": "umbral", "type": "PRODUCT_DECISION", "description": "Umbral de días", "affects": ["Etapa 2"]}],
                 "roadmap": {
                   "objective": {"name": "Kids en Ocaña", "criteria": "Se registra asistencia real", "scope": ["Etapa 1", "openspec:kids"]},
                   "stages": [
                     {"key": "Etapa 1", "title": "Etapa 1 — Base", "marker": "in_progress", "has_criteria": true,
                      "tasks": [{"text": "A", "line": 10, "checked": true}, {"text": "B", "line": 11, "checked": %s}]},
                     {"key": "Etapa 2", "title": "Etapa 2 — Umbral", "marker": "none", "has_criteria": true,
                      "tasks": [{"text": "C", "line": 20, "checked": false}]}]},
                 "openspec": {"specs_count": 19, "archived_count": 12, "commands": ["apply", "archive"],
                   "changes": [%s]}}""".formatted(secondChecked, kids);
    }

    private SyncRun apply(Instant at, ProjectSnapshot... snapshots) {
        return sync.applySnapshot(new SnapshotBatch(2, "test", java.time.OffsetDateTime.parse("2026-10-15T07:00:00-05:00"),
                List.of(snapshots), Map.of(), Map.of(), Map.of()), at).block();
    }

    @Test
    void createsThenIgnoresIdenticalContentAndRoundTripsJsonb() {
        assertThat(apply(T1, snapshot("nolost", nolost(false, null))).created()).containsExactly("nolost");
        assertThat(apply(T2, snapshot("nolost", nolost(false, null))).unchanged()).containsExactly("nolost");

        assertThat(count("SELECT count(*) FROM objective_progress_point")).isEqualTo(1);
        var p = projects.findProject("nolost").block();
        assertThat(p.objective().done()).isEqualTo(2);
        assertThat(p.objective().total()).isEqualTo(4);
        assertThat(p.objective().scope()).extracting(s -> s.ref()).containsExactly("Etapa 1", "openspec:kids");
        assertThat(p.blockers().get(0).openTasksAffected()).isEqualTo(1);
        assertThat(p.openspecCommands()).containsExactly("apply", "archive");
        assertThat(projects.findTasks("nolost").collectList().block())
                .filteredOn(t -> t.text().equals("C")).singleElement()
                .satisfies(t -> {
                    assertThat(t.heldByBlocker()).isEqualTo("umbral");
                    assertThat(t.stageOrdinal()).isEqualTo(1);
                });
        assertThat(projects.findChange("nolost", "kids").block().artifacts().proposal().content()).isEqualTo("# P");
        assertThat(projects.findChanges("nolost").blockFirst().artifacts()).as("listing skips contents").isNull();
    }

    @Test
    void changedContentReplacesTheWholeProject() {
        apply(T1, snapshot("nolost", nolost(false, null)));

        assertThat(apply(T2, snapshot("nolost", nolost(true, null))).updated()).containsExactly("nolost");

        assertThat(count("SELECT count(*) FROM task")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM roadmap_stage")).isEqualTo(2);
        assertThat(count("SELECT objective_done FROM project")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM objective_progress_point")).isEqualTo(2);
    }

    @Test
    void unreadableChangeKeepsItsLastValidVersion() {
        apply(T1, snapshot("nolost", nolost(false, null)));

        apply(T2, snapshot("nolost", nolost(true, "{\"name\": \"kids\", \"error\": \"ilegible\"}")));

        var kids = projects.findChange("nolost", "kids").block();
        assertThat(kids.parseError()).isEqualTo("ilegible");
        assertThat(kids.artifacts().proposal().content()).isEqualTo("# P");
        assertThat(projects.findTasks("nolost").filter(t -> "kids".equals(t.changeName())).map(t -> t.text())
                .collectList().block()).containsExactly("Uno", "Dos");
        assertThat(count("SELECT objective_done FROM project")).isEqualTo(3);
    }

    @Test
    void changeMissingFromTheSnapshotIsDropped() {
        apply(T1, snapshot("nolost", nolost(false, null)));

        apply(T2, snapshot("nolost", nolost(false, null).replace("\"changes\": [", "\"changes\": [], \"x\": [")));

        assertThat(count("SELECT count(*) FROM openspec_change")).isZero();
        assertThat(count("SELECT count(*) FROM task WHERE change_id IS NOT NULL")).isZero();
    }

    @Test
    void readErrorAndMissingAreStoredOnTheProjectRow() {
        apply(T1, snapshot("nolost", nolost(false, null)), snapshot("hotel", """
                {"name": "Hotel", "repo": "hotel", "state": "ACTIVE"}"""));

        SyncRun run = sync.applySnapshot(new SnapshotBatch(2, "test", java.time.OffsetDateTime.parse("2026-10-16T07:00:00-05:00"),
                List.of(), Map.of(), Map.of("hotel", "sin permiso"), Map.of()), T2).block();

        assertThat(run.missing()).containsExactly("nolost");
        assertThat(projects.findProject("hotel").block().readError()).isEqualTo("sin permiso");
        assertThat(projects.findProject("nolost").block().missingSince()).isEqualTo(T2);
        assertThat(count("SELECT count(*) FROM task WHERE project_id = 'nolost'")).isEqualTo(5);
    }

    @Test
    void unreadableChangeNeverSyncedIsStoredWithoutTasks() {
        apply(T1, snapshot("nolost", nolost(false, "{\"name\": \"kids\", \"error\": \"ilegible\"}")));

        assertThat(one("SELECT parse_error FROM openspec_change WHERE name = 'kids'")).isEqualTo("ilegible");
        assertThat(count("SELECT count(*) FROM task WHERE change_id IS NOT NULL")).isZero();
    }

    @Test
    void missingProjectComesBackClean() {
        apply(T1, snapshot("nolost", nolost(false, null)));
        sync.applySnapshot(new SnapshotBatch(2, "test", java.time.OffsetDateTime.parse("2026-10-16T07:00:00-05:00"),
                List.of(), Map.of("otro", "x"), Map.of(), Map.of()), T2).block();
        assertThat(projects.findProject("nolost").block().missingSince()).isEqualTo(T2);

        apply(T2.plusSeconds(60), snapshot("nolost", nolost(false, null)));

        assertThat(projects.findProject("nolost").block().missingSince()).isNull();
    }

    @Test
    void ruleVersionChangeForcesReapplication() {
        apply(T1, snapshot("nolost", nolost(false, null)));
        db.sql("UPDATE project SET progress_rule_version = 0").then().block();

        assertThat(apply(T2, snapshot("nolost", nolost(false, null))).updated()).containsExactly("nolost");
    }

    @Test
    void archivedChangeInScopeCountsTowardsTheObjective() {
        apply(T1, snapshot("hub", """
                {"name": "Hub", "repo": "hub", "state": "ACTIVE",
                 "roadmap": {"objective": {"name": "Pagos", "scope": ["openspec:pagos"]}, "stages": []},
                 "openspec": {"changes": [], "archived_in_scope": [{"name": "2026-09-01-pagos",
                   "tasks": [{"number": "1.1", "text": "x", "line": 1, "checked": true}]}]}}"""));

        var objective = projects.findProject("hub").block().objective();
        assertThat(objective.done()).isEqualTo(1);
        assertThat(objective.total()).isEqualTo(1);
        assertThat(one("SELECT archived FROM openspec_change")).isEqualTo(true);
    }

    @Test
    void portfolioReadsTheStoredStateBack() {
        apply(T1, snapshot("nolost", nolost(false, null)));

        var detail = portfolio.getProject("nolost").block();

        assertThat(detail.card().objective().percent()).isEqualTo(50);
        assertThat(detail.objective().groups()).extracting(g -> g.label()).containsExactly("Etapa 1 — Base", "kids");
        assertThat(detail.history()).hasSize(1);
        assertThat(portfolio.getChanges("nolost").block().changes().get(0).artifacts())
                .containsExactly("proposal", "metadata");
    }
}
