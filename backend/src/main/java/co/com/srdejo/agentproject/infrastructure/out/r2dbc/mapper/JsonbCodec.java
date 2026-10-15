package co.com.srdejo.agentproject.infrastructure.out.r2dbc.mapper;

import io.r2dbc.postgresql.codec.Json;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** JSONB columns travel as {@link Json}; this turns them into domain values and back. */
public class JsonbCodec {

    private final JsonMapper mapper;

    public JsonbCodec(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public Json write(Object value) {
        return value == null ? null : Json.of(mapper.writeValueAsString(value));
    }

    public <T> T read(Json json, TypeReference<T> type, T whenNull) {
        return json == null ? whenNull : mapper.readValue(json.asString(), type);
    }
}
