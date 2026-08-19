package co.com.srdejo.agentproject.parser.service;

import co.com.srdejo.agentproject.parser.api.BatchParseResult;
import co.com.srdejo.agentproject.parser.api.SyncPayload;
import co.com.srdejo.agentproject.parser.api.SyncValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates and normalizes the batch JSON dropped by OpenClaw into the inbox
 * (progreso.json / nuevo.json — see docs/SYNC_PROTOCOL.md): a map of project id -> payload.
 * Each entry is validated independently — never invent progress, and never let one bad
 * entry block the rest of the file.
 */
@Component
public class SyncPayloadParser {

    private static final Set<String> VALID_STATUSES = Set.of("IN_PROGRESS", "BLOCKED", "STARTED", "COMPLETED");
    private static final Set<String> VALID_VERIFY = Set.of("PASSED", "ATTENTION", "PENDING");
    private static final Set<String> VALID_TASK_STATUSES = Set.of("done", "wip", "blocked", "todo");

    private final ObjectMapper objectMapper;

    public SyncPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BatchParseResult parseBatch(String rawJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new SyncValidationException("Invalid JSON: " + e.getMessage(), e);
        }
        if (!root.isObject()) {
            throw new SyncValidationException("Root of batch file must be an object keyed by project id");
        }

        Map<String, SyncPayload> valid = new LinkedHashMap<>();
        Map<String, String> rejected = new LinkedHashMap<>();

        Iterator<String> ids = root.fieldNames();
        while (ids.hasNext()) {
            String id = ids.next();
            try {
                valid.put(id, parseEntry(id, root.get(id)));
            } catch (SyncValidationException e) {
                rejected.put(id, e.getMessage());
            }
        }

        return new BatchParseResult(valid, rejected);
    }

    private SyncPayload parseEntry(String id, JsonNode node) {
        String name = requiredText(node, "name");
        String repo = requiredText(node, "repo");
        int progress = requiredInt(node, "progress");
        String status = requiredEnum(node, "status", VALID_STATUSES);
        String verify = requiredEnum(node, "verify", VALID_VERIFY);
        Instant lastModified = requiredInstant(node, "last_modified");

        if (progress < 0 || progress > 100) {
            throw new SyncValidationException("Field 'progress' must be between 0 and 100, got " + progress);
        }

        return new SyncPayload(
                id,
                name,
                repo,
                progress,
                optionalText(node, "stage"),
                status,
                optionalText(node, "updated"),
                optionalText(node, "commit"),
                verify,
                optionalText(node, "summary"),
                textList(node, "stack"),
                taskList(node),
                checkList(node),
                eventList(node),
                lastModified
        );
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new SyncValidationException("Missing required field: " + field);
        }
        return value.asText();
    }

    private int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            throw new SyncValidationException("Missing or non-numeric required field: " + field);
        }
        return value.asInt();
    }

    private String requiredEnum(JsonNode node, String field, Set<String> allowed) {
        String value = requiredText(node, field);
        if (!allowed.contains(value)) {
            throw new SyncValidationException("Field '" + field + "' must be one of " + allowed + ", got " + value);
        }
        return value;
    }

    private Instant requiredInstant(JsonNode node, String field) {
        String text = requiredText(node, field);
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            throw new SyncValidationException("Field '" + field + "' must be an ISO-8601 datetime, got " + text);
        }
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private List<String> textList(JsonNode node, String field) {
        JsonNode value = node.get(field);
        List<String> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    private List<SyncPayload.Task> taskList(JsonNode node) {
        JsonNode value = node.get("tasks");
        List<SyncPayload.Task> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            for (JsonNode item : value) {
                String taskStatus = item.path("status").asText("");
                if (!VALID_TASK_STATUSES.contains(taskStatus)) {
                    throw new SyncValidationException("Task status must be one of " + VALID_TASK_STATUSES + ", got " + taskStatus);
                }
                result.add(new SyncPayload.Task(
                        item.path("name").asText(""),
                        item.path("stage").asText(""),
                        taskStatus,
                        item.path("date").asText(""),
                        item.path("commit").asText("")
                ));
            }
        }
        return result;
    }

    private List<SyncPayload.TaskCheck> checkList(JsonNode node) {
        JsonNode value = node.get("checks");
        List<SyncPayload.TaskCheck> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> result.add(new SyncPayload.TaskCheck(
                    item.path("name").asText(""),
                    item.path("ok").asBoolean(false),
                    item.path("duration").asText("")
            )));
        }
        return result;
    }

    private List<SyncPayload.AgentEvent> eventList(JsonNode node) {
        JsonNode value = node.get("events");
        List<SyncPayload.AgentEvent> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(item -> result.add(new SyncPayload.AgentEvent(
                    item.path("time").asText(""),
                    item.path("mark").asText(""),
                    item.path("text").asText("")
            )));
        }
        return result;
    }
}
