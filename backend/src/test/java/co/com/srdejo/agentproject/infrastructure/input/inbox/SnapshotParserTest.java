package co.com.srdejo.agentproject.infrastructure.input.inbox;

import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.SnapshotBatch;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotParserTest {

    private final SnapshotParser parser = new SnapshotParser(JsonMapper.builder().build());

    private static String snapshot(String projects) {
        return """
                {"format_version": 2, "generator_version": "test", "generated_at": "2026-10-15T07:00:00-05:00",
                 "projects": %s}""".formatted(projects);
    }

    private static ProjectSnapshot only(SnapshotBatch batch, String id) {
        return batch.valid().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    private static final String MINIMAL = """
            {"name": "Hotel", "repo": "hotel", "state": "ACTIVE"}""";

    @Test
    void protocolDocumentExampleIsValid() throws IOException {
        String doc = Files.readString(Path.of("../docs/SYNC_PROTOCOL.md"));
        Matcher m = Pattern.compile("## Esquema\\s+```json\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL).matcher(doc);
        assertThat(m.find()).as("bloque JSON de la sección Esquema").isTrue();

        SnapshotBatch result = parser.parse(m.group(1));

        assertThat(result.rejected()).isEmpty();
        assertThat(result.readErrors()).containsKey("contact");
        ProjectSnapshot distriapp = only(result, "distriapp");
        assertThat(distriapp.roadmap().objective().scope())
                .containsExactly("Etapa 1", "Etapa 2", "openspec:mvp-e2e-sin-mocks");
        assertThat(distriapp.roadmap().stages().get(0).tasks().get(1).cancelled()).isTrue();
        assertThat(distriapp.openspec().changes()).extracting(ProjectSnapshot.Change::error)
                .containsExactly(null, "tasks.md: permiso denegado");
        assertThat(distriapp.openspec().changes().get(0).tasks().get(0).number()).isEqualTo("1.1");
        assertThat(distriapp.openspec().commands()).contains("apply", "archive");
    }

    @Test
    void rejectsUnsupportedFormatVersion() {
        String json = snapshot("{}").replace("\"format_version\": 2", "\"format_version\": 3");

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(SnapshotValidationException.class)
                .hasMessageContaining("format_version no soportado: 3");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("{no es json"))
                .isInstanceOf(SnapshotValidationException.class)
                .hasMessageContaining("JSON inválido");
    }

    @Test
    void rejectsRootThatIsNotAnObject() {
        assertThatThrownBy(() -> parser.parse("[]")).isInstanceOf(SnapshotValidationException.class);
    }

    @Test
    void invalidEntryIsRejectedWithoutAffectingOthers() {
        SnapshotBatch result = parser.parse(snapshot("""
                {"hotel": %s, "nolost": {"name": "NoLost", "repo": "nolost", "state": "NOW"}}""".formatted(MINIMAL)));

        assertThat(result.valid()).extracting(ProjectSnapshot::id).containsExactly("hotel");
        assertThat(result.rejected()).containsEntry("nolost", "state inválido: NOW");
    }

    @Test
    void priorityRankBelowOneRejectsEntry() {
        SnapshotBatch result = parser.parse(snapshot("""
                {"hotel": {"name": "Hotel", "repo": "hotel", "state": "ACTIVE", "priority_rank": 0}}"""));

        assertThat(result.rejected()).containsKey("hotel");
    }

    @Test
    void blockerWithUnknownTypeIsDroppedAsWarning() {
        SnapshotBatch result = parser.parse(snapshot("""
                {"hub": {"name": "Hub", "repo": "hub", "state": "ACTIVE", "blockers": [
                  {"id": "legal", "type": "LEGAL", "description": "x"},
                  {"id": "pagos", "type": "PRODUCT_DECISION", "description": "Elegir proveedor", "affects": ["Fase 3", "Fase 4"]}
                ]}}"""));

        ProjectSnapshot hub = only(result, "hub");
        assertThat(hub.blockers()).extracting(ProjectSnapshot.Blocker::id).containsExactly("pagos");
        assertThat(hub.blockers().get(0).affects()).containsExactly("Fase 3", "Fase 4");
        assertThat(result.warnings().get("hub")).singleElement().asString().contains("LEGAL");
    }

    @Test
    void changeWithErrorTravelsWithoutTasks() {
        SnapshotBatch result = parser.parse(snapshot("""
                {"nolost": {"name": "NoLost", "repo": "nolost", "state": "ACTIVE",
                  "openspec": {"commands": ["apply"], "changes": [{"name": "kids", "error": "ilegible"}]}}}"""));

        ProjectSnapshot.Change kids = only(result, "nolost").openspec().changes().get(0);
        assertThat(kids.error()).isEqualTo("ilegible");
        assertThat(kids.tasks()).isEmpty();
    }

    @Test
    void entryWithRepoErrorIsReportedSeparately() {
        SnapshotBatch result = parser.parse(snapshot("""
                {"distriapp": {"error": "ROADMAP.md: permiso denegado"}}"""));

        assertThat(result.valid()).isEmpty();
        assertThat(result.readErrors()).containsEntry("distriapp", "ROADMAP.md: permiso denegado");
    }

    @Test
    void malformedRoadmapRejectsTheEntry() {
        SnapshotBatch result = parser.parse(snapshot("""
                {"hotel": {"name": "Hotel", "repo": "hotel", "state": "ACTIVE",
                  "roadmap": {"stages": [{"title": "Etapa 1", "marker": "verde", "tasks": []}]}}}"""));

        assertThat(result.rejected().get("hotel")).contains("marker");
    }

    @Test
    void hashIgnoresKeyOrderButNotContent() {
        String a = only(parser.parse(snapshot("{\"h\": {\"name\": \"H\", \"repo\": \"h\", \"state\": \"ACTIVE\"}}")), "h")
                .contentHash();
        String b = only(parser.parse(snapshot("{\"h\": {\"state\": \"ACTIVE\", \"repo\": \"h\", \"name\": \"H\"}}")), "h")
                .contentHash();
        String c = only(parser.parse(snapshot("{\"h\": {\"state\": \"PAUSED\", \"repo\": \"h\", \"name\": \"H\"}}")), "h")
                .contentHash();

        assertThat(a).isEqualTo(b).isNotEqualTo(c);
    }

    @Test
    void activityIsReadPerProjectAndDropsInvalidCommits() {
        SnapshotBatch result = parser.parse(snapshot("{\"hotel\": " + MINIMAL + "}").replaceFirst("\\}$", """
                , "activity": {"since": "2026-10-08T07:00:00-05:00", "projects": {
                  "hotel": [{"sha": "abc1234", "at": "2026-10-14T18:30:00-05:00", "subject": "feat: reservas"},
                            {"at": "2026-10-14T18:00:00-05:00", "subject": "sin sha"}],
                  "nolost": []}}}"""));

        assertThat(result.activity()).containsOnlyKeys("hotel", "nolost");
        assertThat(result.activity().get("hotel")).singleElement().satisfies(c -> {
            assertThat(c.sha()).isEqualTo("abc1234");
            assertThat(c.committedAt()).isEqualTo(java.time.Instant.parse("2026-10-14T23:30:00Z"));
            assertThat(c.subject()).isEqualTo("feat: reservas");
        });
        assertThat(result.activity().get("nolost")).isEmpty();
        assertThat(result.warnings().get("hotel")).singleElement().asString().contains("commit descartado (sin sha)");
        assertThat(result.valid()).hasSize(1);
    }

    @Test
    void withoutActivityTheBlockIsNull() {
        SnapshotBatch result = parser.parse(snapshot("{\"hotel\": " + MINIMAL + "}"));

        assertThat(result.activity()).isNull();
        assertThat(result.valid()).hasSize(1);
    }

    @Test
    void activityDoesNotChangeTheEntryHash() {
        String plain = only(parser.parse(snapshot("{\"hotel\": " + MINIMAL + "}")), "hotel").contentHash();
        String withActivity = only(parser.parse(snapshot("{\"hotel\": " + MINIMAL + "}").replaceFirst("\\}$",
                ", \"activity\": {\"projects\": {\"hotel\": [{\"sha\": \"a\", \"at\": \"2026-10-14T18:30:00-05:00\", "
                        + "\"subject\": \"x\"}]}}}")), "hotel").contentHash();

        assertThat(withActivity).isEqualTo(plain);
    }
}
