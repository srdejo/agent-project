package co.com.srdejo.agentproject.infrastructure.input.inbox;

import co.com.srdejo.agentproject.domain.model.ProjectSnapshot;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.AchievedObjective;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.ArchivedChange;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.Artifact;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.Artifacts;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.Blocker;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.Change;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.ChangeTask;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.Objective;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.OpenSpec;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.Roadmap;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.RoadmapTask;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.SpecArtifact;
import co.com.srdejo.agentproject.domain.model.ProjectSnapshot.Stage;
import co.com.srdejo.agentproject.domain.model.SnapshotBatch;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Validates the v2 snapshot dropped in the inbox by the portfolio generator (docs/SYNC_PROTOCOL.md) and turns it
 * into domain snapshots. File-level problems throw {@link SnapshotValidationException}; everything else is judged
 * per entry, so one bad project never blocks the rest. The JSON format is an input detail: the domain never sees it.
 */
public class SnapshotParser {

    public static final int SUPPORTED_FORMAT_VERSION = 2;

    static final Set<String> PROJECT_STATES =
            Set.of("ACTIVE", "NEXT", "PAUSED", "BLOCKED", "MAINTENANCE", "COMPLETED", "ARCHIVED");
    static final Set<String> BLOCKER_TYPES =
            Set.of("PRODUCT_DECISION", "EXTERNAL_DEPENDENCY", "TECHNICAL", "ENVIRONMENT", "SECURITY", "UNKNOWN");
    static final Set<String> STAGE_MARKERS = Set.of("closed", "in_progress", "none");

    private final JsonMapper mapper;
    private final JsonMapper canonicalMapper;

    public SnapshotParser(JsonMapper mapper) {
        this.mapper = mapper;
        this.canonicalMapper = mapper.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    }

    public SnapshotBatch parse(String rawJson) {
        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (JacksonException e) {
            throw new SnapshotValidationException("JSON inválido: " + e.getOriginalMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new SnapshotValidationException("La raíz del snapshot debe ser un objeto");
        }
        JsonNode version = root.get("format_version");
        if (version == null || !version.isInt()) {
            throw new SnapshotValidationException("Falta 'format_version' entero");
        }
        if (version.asInt() != SUPPORTED_FORMAT_VERSION) {
            throw new SnapshotValidationException("format_version no soportado: " + version.asInt()
                    + " (se espera " + SUPPORTED_FORMAT_VERSION + ")");
        }
        JsonNode projects = root.get("projects");
        if (projects == null || !projects.isObject()) {
            throw new SnapshotValidationException("Falta 'projects' como objeto indexado por id");
        }
        OffsetDateTime generatedAt;
        try {
            generatedAt = OffsetDateTime.parse(root.path("generated_at").asString(""));
        } catch (DateTimeParseException e) {
            throw new SnapshotValidationException("'generated_at' debe ser ISO-8601 con offset", e);
        }

        List<ProjectSnapshot> valid = new ArrayList<>();
        Map<String, String> rejected = new LinkedHashMap<>();
        Map<String, String> readErrors = new LinkedHashMap<>();
        Map<String, List<String>> warnings = new LinkedHashMap<>();

        for (String id : projects.propertyNames()) {
            JsonNode node = projects.get(id);
            if (node.isObject() && node.hasNonNull("error")) {
                readErrors.put(id, node.get("error").asString());
                continue;
            }
            List<String> entryWarnings = new ArrayList<>();
            try {
                valid.add(parseEntry(id, node, entryWarnings));
            } catch (EntryException e) {
                rejected.put(id, e.getMessage());
            }
            if (!entryWarnings.isEmpty()) {
                warnings.put(id, entryWarnings);
            }
        }

        return new SnapshotBatch(version.asInt(), textOrNull(root, "generator_version"), generatedAt, valid, rejected,
                readErrors, warnings);
    }

    private ProjectSnapshot parseEntry(String id, JsonNode node, List<String> warnings) {
        if (!node.isObject()) {
            throw new EntryException("la entrada debe ser un objeto");
        }
        String state = requiredText(node, "state");
        if (!PROJECT_STATES.contains(state)) {
            throw new EntryException("state inválido: " + state);
        }
        Integer priorityRank = null;
        if (node.hasNonNull("priority_rank")) {
            JsonNode rank = node.get("priority_rank");
            if (!rank.isInt() || rank.asInt() < 1) {
                throw new EntryException("priority_rank debe ser un entero >= 1");
            }
            priorityRank = rank.asInt();
        }
        OffsetDateTime sourceModifiedAt = null;
        if (node.hasNonNull("source_modified_at")) {
            try {
                sourceModifiedAt = OffsetDateTime.parse(node.get("source_modified_at").asString());
            } catch (DateTimeParseException e) {
                throw new EntryException("source_modified_at debe ser ISO-8601 con offset");
            }
        }

        return new ProjectSnapshot(
                id,
                hash(node),
                requiredText(node, "name"),
                requiredText(node, "repo"),
                textOrNull(node, "alias"),
                textOrNull(node, "summary"),
                state,
                priorityRank,
                stringList(node, "stack"),
                blockers(node, warnings),
                textOrNull(node, "source_commit"),
                sourceModifiedAt,
                node.hasNonNull("roadmap") ? roadmap(node.get("roadmap")) : null,
                node.hasNonNull("openspec") ? openspec(node.get("openspec")) : null
        );
    }

    private List<Blocker> blockers(JsonNode node, List<String> warnings) {
        List<Blocker> result = new ArrayList<>();
        for (JsonNode b : array(node, "blockers")) {
            String key = textOrNull(b, "id");
            String type = textOrNull(b, "type");
            String description = textOrNull(b, "description");
            if (key == null || description == null || type == null || !BLOCKER_TYPES.contains(type)) {
                warnings.add("bloqueo descartado (" + (key == null ? "sin id" : key) + "): "
                        + (type != null && !BLOCKER_TYPES.contains(type) ? "type inválido " + type
                        : "faltan id, type o description"));
                continue;
            }
            LocalDate created = null;
            if (b.hasNonNull("created")) {
                try {
                    created = LocalDate.parse(b.get("created").asString());
                } catch (DateTimeParseException e) {
                    warnings.add("bloqueo " + key + ": 'created' ignorado, no es AAAA-MM-DD");
                }
            }
            result.add(new Blocker(key, type, description, textOrNull(b, "impact"), stringList(b, "affects"),
                    textOrNull(b, "owner"), created, textOrNull(b, "resolution")));
        }
        return result;
    }

    private Roadmap roadmap(JsonNode node) {
        requireObject(node, "roadmap");
        Objective objective = null;
        if (node.hasNonNull("objective")) {
            JsonNode o = node.get("objective");
            requireObject(o, "roadmap.objective");
            objective = new Objective(requiredText(o, "name"), textOrNull(o, "criteria"), stringList(o, "scope"),
                    optionalDate(o, "target_date"));
        }
        List<AchievedObjective> achieved = mapArray(node, "achieved",
                a -> new AchievedObjective(optionalDate(a, "date"), requiredText(a, "name"), textOrNull(a, "criteria")));
        List<Stage> stages = mapArray(node, "stages", s -> {
            String marker = textOrNull(s, "marker");
            if (marker == null || !STAGE_MARKERS.contains(marker)) {
                throw new EntryException("marker de etapa inválido: " + marker);
            }
            return new Stage(textOrNull(s, "key"), requiredText(s, "title"), marker,
                    s.path("has_criteria").asBoolean(false), s.path("backlog").asBoolean(false),
                    mapArray(s, "tasks", t -> new RoadmapTask(requiredText(t, "text"), t.path("line").asInt(0),
                            requiredBoolean(t, "checked"), t.path("cancelled").asBoolean(false))));
        });
        return new Roadmap(objective, achieved, stages);
    }

    private OpenSpec openspec(JsonNode node) {
        requireObject(node, "openspec");
        List<Change> changes = mapArray(node, "changes", c -> {
            String name = requiredText(c, "name");
            if (c.hasNonNull("error")) {
                return new Change(name, c.get("error").asString(), null, null, List.of());
            }
            return new Change(name, null, textOrNull(c, "metadata"), artifacts(c.path("artifacts")), changeTasks(c));
        });
        List<ArchivedChange> archived = mapArray(node, "archived_in_scope",
                a -> new ArchivedChange(requiredText(a, "name"), changeTasks(a)));
        return new OpenSpec(node.path("specs_count").asInt(0), node.path("archived_count").asInt(0),
                stringList(node, "commands"), changes, archived);
    }

    private Artifacts artifacts(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return new Artifacts(null, null, null, List.of());
        }
        requireObject(node, "artifacts");
        return new Artifacts(artifact(node.get("proposal")), artifact(node.get("design")), artifact(node.get("tasks")),
                mapArray(node, "specs", s -> new SpecArtifact(requiredText(s, "path"), requiredText(s, "content"),
                        s.path("truncated").asBoolean(false))));
    }

    private Artifact artifact(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return new Artifact(requiredText(node, "content"), node.path("truncated").asBoolean(false));
    }

    private List<ChangeTask> changeTasks(JsonNode node) {
        return mapArray(node, "tasks", t -> new ChangeTask(t.path("number").asString(""), requiredText(t, "text"),
                textOrNull(t, "section"), t.path("line").asInt(0), requiredBoolean(t, "checked"),
                t.path("cancelled").asBoolean(false)));
    }

    private String hash(JsonNode node) {
        try {
            Object plain = mapper.treeToValue(node, Object.class);
            byte[] canonical = canonicalMapper.writeValueAsString(plain).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static <T> List<T> mapArray(JsonNode node, String field, Function<JsonNode, T> fn) {
        List<T> result = new ArrayList<>();
        for (JsonNode item : array(node, field)) {
            requireObject(item, field + "[]");
            result.add(fn.apply(item));
        }
        return result;
    }

    private static Iterable<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new EntryException("'" + field + "' debe ser una lista");
        }
        return value;
    }

    private static List<String> stringList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : array(node, field)) {
            if (!item.isString()) {
                throw new EntryException("'" + field + "' debe ser una lista de textos");
            }
            result.add(item.asString());
        }
        return result;
    }

    private static void requireObject(JsonNode node, String field) {
        if (!node.isObject()) {
            throw new EntryException("'" + field + "' debe ser un objeto");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new EntryException("falta el campo requerido '" + field + "'");
        }
        return value.asString();
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new EntryException("'" + field + "' debe ser booleano");
        }
        return value.asBoolean();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asString("").isBlank() ? null : value.asString();
    }

    private static LocalDate optionalDate(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new EntryException("'" + field + "' debe ser AAAA-MM-DD");
        }
    }

    private static final class EntryException extends RuntimeException {
        EntryException(String message) {
            super(message);
        }
    }
}
