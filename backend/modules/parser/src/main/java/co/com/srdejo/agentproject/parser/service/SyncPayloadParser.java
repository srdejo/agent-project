package co.com.srdejo.agentproject.parser.service;

import co.com.srdejo.agentproject.parser.api.SyncPayload;
import co.com.srdejo.agentproject.parser.api.SyncValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates and normalizes the JSON dropped by OpenClaw into the inbox — see docs/SYNC_PROTOCOL.md.
 * Rejects anything that doesn't meet the minimum contract instead of guessing values (never invent progress).
 */
@Component
public class SyncPayloadParser {

    private static final Set<String> VALID_STATUSES = Set.of("IN_PROGRESS", "BLOCKED", "STARTED", "COMPLETED");
    private static final Set<String> VALID_VERIFY = Set.of("PASSED", "ATTENTION", "PENDING");

    private final ObjectMapper objectMapper;

    public SyncPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SyncPayload parse(String rawJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new SyncValidationException("Invalid JSON: " + e.getMessage(), e);
        }

        String id = requiredText(root, "id");
        String name = requiredText(root, "name");
        String repo = requiredText(root, "repo");
        int progress = requiredInt(root, "progress");
        String status = requiredEnum(root, "status", VALID_STATUSES);
        String verify = requiredEnum(root, "verify", VALID_VERIFY);

        if (progress < 0 || progress > 100) {
            throw new SyncValidationException("Field 'progress' must be between 0 and 100, got " + progress);
        }

        return new SyncPayload(
                id,
                name,
                repo,
                progress,
                optionalText(root, "stage"),
                status,
                optionalText(root, "updated"),
                optionalText(root, "commit"),
                verify,
                textList(root, "completed"),
                textList(root, "next"),
                textList(root, "blocked"),
                checkList(root),
                eventList(root)
        );
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new SyncValidationException("Missing required field: " + field);
        }
        return node.asText();
    }

    private int requiredInt(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new SyncValidationException("Missing or non-numeric required field: " + field);
        }
        return node.asInt();
    }

    private String requiredEnum(JsonNode root, String field, Set<String> allowed) {
        String value = requiredText(root, field);
        if (!allowed.contains(value)) {
            throw new SyncValidationException("Field '" + field + "' must be one of " + allowed + ", got " + value);
        }
        return value;
    }

    private String optionalText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private List<String> textList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    private List<SyncPayload.TaskCheck> checkList(JsonNode root) {
        JsonNode node = root.get("checks");
        List<SyncPayload.TaskCheck> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(new SyncPayload.TaskCheck(
                    item.path("name").asText(""),
                    item.path("ok").asBoolean(false),
                    item.path("duration").asText("")
            )));
        }
        return result;
    }

    private List<SyncPayload.AgentEvent> eventList(JsonNode root) {
        JsonNode node = root.get("events");
        List<SyncPayload.AgentEvent> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> result.add(new SyncPayload.AgentEvent(
                    item.path("time").asText(""),
                    item.path("mark").asText(""),
                    item.path("text").asText("")
            )));
        }
        return result;
    }
}
