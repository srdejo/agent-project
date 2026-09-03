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
    private static final Set<String> VALID_PRIORITIES = Set.of("NOW", "NEXT", "DECIDE", "ON_TRACK", "FROZEN");
    private static final int ALIAS_MAX_LENGTH = 120;
    private static final int MIN_PRIORITY_RANK = 1;
    /** Value handed downstream to mean "clear the stored rank" — see {@link #optionalRank}. */
    private static final int PRIORITY_RANK_CLEARED = 0;

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

        String alias = nullableText(node, "alias");
        String priority = optionalEnum(node, "priority", VALID_PRIORITIES);
        Integer priorityRank = optionalRank(node, "priority_rank");
        String openQuestion = nullableText(node, "open_question");

        if (alias != null && alias.length() > ALIAS_MAX_LENGTH) {
            throw new SyncValidationException(
                    "Field 'alias' must be at most " + ALIAS_MAX_LENGTH + " characters, got " + alias.length());
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
                alias,
                priority,
                priorityRank,
                openQuestion,
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

    /**
     * Same shape as {@link #optionalText}, but it keeps the distinction the portfolio fields need:
     * a field that is <em>absent</em> from the JSON comes back as {@code null} ("don't touch what is
     * stored"), while a field present with an explicit {@code null} comes back as an empty string
     * ("clear what is stored"). See docs/SYNC_PROTOCOL.md.
     */
    private String nullableText(JsonNode node, String field) {
        if (!node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        return value.isNull() ? "" : value.asText();
    }

    /**
     * Optional enum: absent -> {@code null}, explicit JSON {@code null} -> empty string (clear),
     * present -> validated against {@code allowed}, rejecting the whole entry when it is not.
     */
    private String optionalEnum(JsonNode node, String field, Set<String> allowed) {
        String value = nullableText(node, field);
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!allowed.contains(value)) {
            throw new SyncValidationException("Field '" + field + "' must be one of " + allowed + ", got " + value);
        }
        return value;
    }

    /**
     * Integer sibling of {@link #nullableText} for {@code priority_rank}: absent -> {@code null}
     * ("don't touch what is stored"), explicit JSON {@code null} -> {@link #PRIORITY_RANK_CLEARED}
     * ("clear what is stored"). The empty string is not an option for an {@code Integer}, so
     * {@code 0} plays that role — it is outside the field's own domain, which starts at
     * {@link #MIN_PRIORITY_RANK}. Anything present that is not an integer, or is below the minimum,
     * rejects the whole entry the same way {@code progress} does.
     */
    private Integer optionalRank(JsonNode node, String field) {
        if (!node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value.isNull()) {
            return PRIORITY_RANK_CLEARED;
        }
        if (!value.isIntegralNumber()) {
            throw new SyncValidationException("Field '" + field + "' must be an integer, got " + value.asText());
        }
        int rank = value.asInt();
        if (rank < MIN_PRIORITY_RANK) {
            throw new SyncValidationException(
                    "Field '" + field + "' must be " + MIN_PRIORITY_RANK + " or greater, got " + rank);
        }
        return rank;
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
