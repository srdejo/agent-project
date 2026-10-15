package co.com.srdejo.agentproject.infrastructure.input.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory presence of the local agent (docs/AGENT_MONITOR.md). The authenticated agent pushes status and logs;
 * dashboards only listen. A restart puts the agent back to {@code offline} until its next heartbeat.
 */
@Component
public class AgentPresenceService {

    private static final long OFFLINE_AFTER_SECONDS = 45;
    private static final Sinks.EmitFailureHandler RETRY = Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100));

    private final JsonMapper mapper;
    private final Sinks.Many<String> dashboards = Sinks.many().multicast().directBestEffort();
    private final Map<String, Sinks.Many<String>> agents = new ConcurrentHashMap<>();
    private volatile String status = "offline";
    private volatile String activity = "PC local desconectada";
    private volatile Instant lastSeen;

    public AgentPresenceService(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /** What a dashboard receives: the current snapshot, then every broadcast. */
    public Flux<String> dashboardStream() {
        return Flux.just(snapshot()).concatWith(dashboards.asFlux());
    }

    /** Registers the agent (replacing any previous one) and returns what must be sent to it. */
    public Flux<String> registerAgent(String sessionId) {
        agents.values().forEach(previous -> previous.emitComplete(RETRY));
        agents.clear();
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        agents.put(sessionId, outbound);
        lastSeen = Instant.now();
        status = "online";
        activity = "Agente conectado y en espera";
        broadcast(snapshot());
        return outbound.asFlux();
    }

    public void removeAgent(String sessionId) {
        if (agents.remove(sessionId) != null) {
            markOffline();
        }
    }

    public void sendToAgent(String sessionId, String message) {
        Sinks.Many<String> sink = agents.get(sessionId);
        if (sink != null) {
            sink.emitNext(message, RETRY);
        }
    }

    public void receiveAgentMessage(JsonNode message) {
        lastSeen = Instant.now();
        String type = message.path("type").asString("");
        if ("heartbeat".equals(type) || "pong".equals(type)) {
            if ("offline".equals(status)) {
                status = "online";
                activity = "Agente conectado y en espera";
            }
            broadcast(snapshot());
            return;
        }
        if ("agent_status".equals(type)) {
            String next = message.path("status").asString("idle");
            status = "idle".equals(next) ? "online" : next;
            activity = message.path("message").asString(defaultActivity(next));
        }
        if ("log_event".equals(type)) {
            broadcast(mapper.writeValueAsString(message));
        }
        broadcast(snapshot());
    }

    /** Only the agent's own heartbeat sets this: the API answering says nothing about the agent. */
    public boolean isAgentOnline() {
        return !"offline".equals(status);
    }

    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        String ping = mapper.writeValueAsString(Map.of("type", "ping", "timestamp", Instant.now().toString()));
        agents.keySet().forEach(id -> sendToAgent(id, ping));
        if (lastSeen == null || lastSeen.plusSeconds(OFFLINE_AFTER_SECONDS).isBefore(Instant.now())) {
            markOffline();
        }
    }

    private void markOffline() {
        status = "offline";
        activity = "PC local desconectada";
        broadcast(snapshot());
    }

    private String snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", "agent_snapshot");
        snapshot.put("status", status);
        snapshot.put("activity", activity);
        snapshot.put("lastSeen", lastSeen == null ? "" : lastSeen.toString());
        return mapper.writeValueAsString(snapshot);
    }

    private void broadcast(String payload) {
        dashboards.emitNext(payload, RETRY);
    }

    private static String defaultActivity(String agentStatus) {
        return switch (agentStatus) {
            case "receiving_message" -> "Recibiendo mensaje...";
            case "thinking" -> "Pensando respuesta...";
            case "executing_tool" -> "Ejecutando herramienta...";
            case "replying" -> "Enviando respuesta...";
            case "error" -> "Error del agente";
            default -> "Agente conectado y en espera";
        };
    }
}
