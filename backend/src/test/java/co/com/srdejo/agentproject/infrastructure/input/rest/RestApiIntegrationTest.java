package co.com.srdejo.agentproject.infrastructure.input.rest;

import co.com.srdejo.agentproject.domain.api.ISyncServicePort;
import co.com.srdejo.agentproject.infrastructure.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

/** HTTP contract of every read endpoint over seeded data, through the real stack. */
class RestApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ISyncServicePort sync;

    private WebTestClient client;

    @BeforeEach
    void seed() {
        client = WebTestClient.bindToApplicationContext(context).build();
        String snapshot = snapshotJson("""
                {"nolost": {"name": "NoLost", "repo": "nolost", "state": "ACTIVE", "priority_rank": 1,
                   "blockers": [{"id": "umbral", "type": "PRODUCT_DECISION", "description": "Umbral de días", "affects": ["Etapa 2"]}],
                   "roadmap": {
                     "objective": {"name": "Kids", "criteria": "Asistencia real", "scope": ["Etapa 1", "openspec:kids"]},
                     "stages": [
                       {"key": "Etapa 1", "title": "Etapa 1 — Base", "marker": "in_progress", "has_criteria": true,
                        "tasks": [{"text": "A", "line": 10, "checked": true}]},
                       {"key": "Etapa 2", "title": "Etapa 2 — Umbral", "marker": "closed", "has_criteria": true,
                        "tasks": [{"text": "C", "line": 20, "checked": false}]}]},
                   "openspec": {"commands": ["apply", "archive"], "changes": [
                     {"name": "kids", "metadata": "schema: spec-driven",
                      "artifacts": {"proposal": {"content": "<script>alert(1)</script>", "truncated": false}},
                      "tasks": [{"number": "1.1", "text": "Uno", "line": 3, "checked": true},
                                {"number": "1.2", "text": "Dos", "line": 4, "checked": false}]}]}},
                 "hotel": {"name": "Hotel", "repo": "hotel", "state": "ACTIVE", "priority_rank": 2}}""");
        sync.applySnapshot(parser.parse(snapshot), Instant.now()).block();
    }

    @Test
    void portfolioCardsCarryObjectiveRoadmapAndBlockers() {
        client.get().uri("/api/portfolio").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("nolost")
                .jsonPath("$[0].objective.done").isEqualTo(2)
                .jsonPath("$[0].objective.total").isEqualTo(3)
                .jsonPath("$[0].objective.percent").isEqualTo(67)
                .jsonPath("$[0].roadmap.done").isEqualTo(1)
                .jsonPath("$[0].roadmap.total").isEqualTo(2)
                .jsonPath("$[0].mainBlocker.id").isEqualTo("umbral")
                .jsonPath("$[0].mainBlocker.openTasksAffected").isEqualTo(1)
                .jsonPath("$[0].hasActionable").isEqualTo(true)
                .jsonPath("$[1].id").isEqualTo("hotel")
                .jsonPath("$[1].objective").doesNotExist();
    }

    @Test
    void projectDetailExposesGroupsStagesAndEvidenceState() {
        client.get().uri("/api/projects/nolost").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.card.id").isEqualTo("nolost")
                .jsonPath("$.objective.scope[1].kind").isEqualTo("CHANGE")
                .jsonPath("$.objective.groups[1].label").isEqualTo("kids")
                .jsonPath("$.stages[1].tasks[0].heldByBlocker").isEqualTo("umbral")
                .jsonPath("$.history.length()").isEqualTo(1)
                .jsonPath("$.evidence").isEqualTo("NOT_AVAILABLE");
    }

    @Test
    void changesAndChangeDetailReturnArtifactsAsRawText() {
        client.get().uri("/api/projects/nolost/changes").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openspecDetected").isEqualTo(true)
                .jsonPath("$.changes[0].artifacts[0]").isEqualTo("proposal")
                .jsonPath("$.changes[0].done").isEqualTo(1);
        client.get().uri("/api/projects/nolost/changes/kids").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.artifacts.proposal.content").isEqualTo("<script>alert(1)</script>")
                .jsonPath("$.artifacts.design").doesNotExist()
                .jsonPath("$.path").isEqualTo("openspec/changes/kids/");
    }

    @Test
    void continuationIsPlainTextWithTheSuggestedCommand() {
        client.get().uri("/api/projects/nolost/changes/kids/continuation").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.suggestedCommand").isEqualTo("/opsx:apply kids")
                .jsonPath("$.text").value(text -> org.assertj.core.api.Assertions.assertThat((String) text)
                        .contains("Siguiente paso: 1.2 Dos"));
    }

    @Test
    void workUsesTheContinueSectionName() {
        client.get().uri("/api/work").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.continue[0].changeName").isEqualTo("kids")
                .jsonPath("$.decisions[0].blockerId").isEqualTo("umbral")
                .jsonPath("$.review[0].kind").isEqualTo("NO_OBJECTIVE")
                .jsonPath("$.activity[0].kind").isEqualTo("SYNC");
    }

    @Test
    void syncHealthReportsDataAndAgentSeparately() {
        client.get().uri("/api/health/sync").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lastSuccessfulSync").exists()
                .jsonPath("$.lastRun.created.length()").isEqualTo(2)
                .jsonPath("$.agentConnectivity").isEqualTo("offline");
    }

    @Test
    void unknownProjectOrChangeIs404WithAJsonMessage() {
        client.get().uri("/api/projects/nope").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.message").isEqualTo("Proyecto no encontrado: nope");
        client.get().uri("/api/projects/nope/changes").exchange().expectStatus().isNotFound();
        client.get().uri("/api/projects/nolost/changes/nope").exchange().expectStatus().isNotFound();
        client.get().uri("/api/projects/nolost/changes/nope/continuation").exchange().expectStatus().isNotFound();
    }

    @Test
    void openApiDocumentListsTheEndpoints() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/portfolio']").exists()
                .jsonPath("$.paths['/api/health/sync']").exists();
    }
}
