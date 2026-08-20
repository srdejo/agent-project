package co.com.srdejo.agentproject.parser.service;

import co.com.srdejo.agentproject.parser.api.BatchParseResult;
import co.com.srdejo.agentproject.parser.api.SyncPayload;
import co.com.srdejo.agentproject.parser.api.SyncValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncPayloadParserTest {

    private SyncPayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new SyncPayloadParser(new ObjectMapper());
    }

    @Test
    void parsesAValidEntry() {
        String json = """
                {
                  "agent-project": {
                    "name": "Agent Project",
                    "repo": "agent-project",
                    "progress": 42,
                    "stage": "backend",
                    "status": "IN_PROGRESS",
                    "verify": "PASSED",
                    "summary": "on track",
                    "stack": ["java", "angular"],
                    "tasks": [
                      {"name": "sync job", "stage": "backend", "status": "done", "date": "2026-08-19", "commit": "abc123"}
                    ],
                    "checks": [
                      {"name": "unit tests", "ok": true, "duration": "12s"}
                    ],
                    "events": [
                      {"time": "10:00", "mark": "note", "text": "started"}
                    ],
                    "last_modified": "2026-08-19T10:00:00Z"
                  }
                }
                """;

        BatchParseResult result = parser.parseBatch(json);

        assertThat(result.rejected()).isEmpty();
        assertThat(result.valid()).containsKey("agent-project");
        SyncPayload payload = result.valid().get("agent-project");
        assertThat(payload.id()).isEqualTo("agent-project");
        assertThat(payload.name()).isEqualTo("Agent Project");
        assertThat(payload.progress()).isEqualTo(42);
        assertThat(payload.status()).isEqualTo("IN_PROGRESS");
        assertThat(payload.verify()).isEqualTo("PASSED");
        assertThat(payload.lastModified()).isEqualTo(Instant.parse("2026-08-19T10:00:00Z"));
        assertThat(payload.stack()).containsExactly("java", "angular");
        assertThat(payload.tasks()).hasSize(1);
        assertThat(payload.tasks().get(0).status()).isEqualTo("done");
        assertThat(payload.checks()).hasSize(1);
        assertThat(payload.events()).hasSize(1);
    }

    @Test
    void normalizesMissingOptionalListsToEmpty() {
        String json = """
                {
                  "agent-project": {
                    "name": "Agent Project",
                    "repo": "agent-project",
                    "progress": 0,
                    "status": "STARTED",
                    "verify": "PENDING",
                    "last_modified": "2026-08-19T10:00:00Z"
                  }
                }
                """;

        SyncPayload payload = parser.parseBatch(json).valid().get("agent-project");

        assertThat(payload.stack()).isEmpty();
        assertThat(payload.tasks()).isEmpty();
        assertThat(payload.checks()).isEmpty();
        assertThat(payload.events()).isEmpty();
        assertThat(payload.stage()).isNull();
        assertThat(payload.summary()).isNull();
    }

    @Test
    void rejectsEntryMissingRequiredFieldWithoutBlockingOthers() {
        String json = """
                {
                  "bad-project": {
                    "repo": "bad",
                    "progress": 10,
                    "status": "STARTED",
                    "verify": "PENDING",
                    "last_modified": "2026-08-19T10:00:00Z"
                  },
                  "good-project": {
                    "name": "Good Project",
                    "repo": "good",
                    "progress": 10,
                    "status": "STARTED",
                    "verify": "PENDING",
                    "last_modified": "2026-08-19T10:00:00Z"
                  }
                }
                """;

        BatchParseResult result = parser.parseBatch(json);

        assertThat(result.rejected()).containsOnlyKeys("bad-project");
        assertThat(result.rejected().get("bad-project")).contains("name");
        assertThat(result.valid()).containsOnlyKeys("good-project");
    }

    @Test
    void rejectsProgressOutOfRange() {
        String json = """
                {
                  "agent-project": {
                    "name": "Agent Project",
                    "repo": "agent-project",
                    "progress": 150,
                    "status": "STARTED",
                    "verify": "PENDING",
                    "last_modified": "2026-08-19T10:00:00Z"
                  }
                }
                """;

        BatchParseResult result = parser.parseBatch(json);

        assertThat(result.valid()).isEmpty();
        assertThat(result.rejected().get("agent-project")).contains("progress");
    }

    @Test
    void rejectsInvalidStatusEnum() {
        String json = """
                {
                  "agent-project": {
                    "name": "Agent Project",
                    "repo": "agent-project",
                    "progress": 10,
                    "status": "NOT_A_STATUS",
                    "verify": "PENDING",
                    "last_modified": "2026-08-19T10:00:00Z"
                  }
                }
                """;

        BatchParseResult result = parser.parseBatch(json);

        assertThat(result.valid()).isEmpty();
        assertThat(result.rejected().get("agent-project")).contains("status");
    }

    @Test
    void rejectsInvalidTaskStatus() {
        String json = """
                {
                  "agent-project": {
                    "name": "Agent Project",
                    "repo": "agent-project",
                    "progress": 10,
                    "status": "STARTED",
                    "verify": "PENDING",
                    "tasks": [
                      {"name": "x", "stage": "backend", "status": "not-a-status", "date": "2026-08-19", "commit": "abc"}
                    ],
                    "last_modified": "2026-08-19T10:00:00Z"
                  }
                }
                """;

        BatchParseResult result = parser.parseBatch(json);

        assertThat(result.valid()).isEmpty();
        assertThat(result.rejected().get("agent-project")).contains("Task status");
    }

    @Test
    void rejectsNonIsoLastModified() {
        String json = """
                {
                  "agent-project": {
                    "name": "Agent Project",
                    "repo": "agent-project",
                    "progress": 10,
                    "status": "STARTED",
                    "verify": "PENDING",
                    "last_modified": "not-a-date"
                  }
                }
                """;

        BatchParseResult result = parser.parseBatch(json);

        assertThat(result.valid()).isEmpty();
        assertThat(result.rejected().get("agent-project")).contains("ISO-8601");
    }

    @Test
    void throwsWhenRootIsNotAnObject() {
        assertThatThrownBy(() -> parser.parseBatch("[1, 2, 3]"))
                .isInstanceOf(SyncValidationException.class)
                .hasMessageContaining("object");
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() -> parser.parseBatch("{not valid json"))
                .isInstanceOf(SyncValidationException.class)
                .hasMessageContaining("Invalid JSON");
    }
}
