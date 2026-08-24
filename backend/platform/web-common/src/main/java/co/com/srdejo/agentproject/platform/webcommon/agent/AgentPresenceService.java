package co.com.srdejo.agentproject.platform.webcommon.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class AgentPresenceService {
    private static final long OFFLINE_AFTER_SECONDS = 45;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArraySet<WebSocketSession> dashboards = new CopyOnWriteArraySet<>();
    private final Map<String, WebSocketSession> agents = new ConcurrentHashMap<>();
    private volatile String status = "offline";
    private volatile String activity = "PC local desconectada";
    private volatile Instant lastSeen;

    public AgentPresenceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void registerDashboard(WebSocketSession session) {
        dashboards.add(session);
        send(session, snapshot());
    }

    public void registerAgent(WebSocketSession session) {
        agents.values().forEach(existing -> close(existing, CloseStatus.SERVICE_RESTARTED));
        agents.put(session.getId(), session);
        lastSeen = Instant.now();
        status = "online";
        activity = "Agente conectado y en espera";
        broadcast(snapshot());
    }

    public void remove(WebSocketSession session) {
        dashboards.remove(session);
        if (agents.remove(session.getId()) != null) {
            markOffline();
        }
    }

    public void receiveAgentMessage(JsonNode message) {
        lastSeen = Instant.now();
        String type = message.path("type").asText("");
        if ("heartbeat".equals(type) || "pong".equals(type)) {
            if ("offline".equals(status)) {
                status = "online";
                activity = "Agente conectado y en espera";
            }
            broadcast(snapshot());
            return;
        }
        if ("agent_status".equals(type)) {
            String nextStatus = message.path("status").asText("idle");
            status = "idle".equals(nextStatus) ? "online" : nextStatus;
            activity = message.path("message").asText(defaultActivity(nextStatus));
        }
        if ("log_event".equals(type)) {
            broadcast(message);
        }
        broadcast(snapshot());
    }

    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        agents.values().forEach(agent -> send(agent, Map.of("type", "ping", "timestamp", Instant.now().toString())));
        if (lastSeen == null || lastSeen.plusSeconds(OFFLINE_AFTER_SECONDS).isBefore(Instant.now())) {
            markOffline();
        }
    }

    private void markOffline() {
        status = "offline";
        activity = "PC local desconectada";
        broadcast(snapshot());
    }

    private Map<String, Object> snapshot() {
        return Map.of("type", "agent_snapshot", "status", status, "activity", activity,
                "lastSeen", lastSeen == null ? "" : lastSeen.toString());
    }

    private String defaultActivity(String agentStatus) {
        return switch (agentStatus) {
            case "receiving_message" -> "Recibiendo mensaje...";
            case "thinking" -> "Pensando respuesta...";
            case "executing_tool" -> "Ejecutando herramienta...";
            case "replying" -> "Enviando respuesta...";
            case "error" -> "Error del agente";
            default -> "Agente conectado y en espera";
        };
    }

    private void broadcast(Object payload) {
        dashboards.forEach(session -> send(session, payload));
    }

    private void send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException exception) {
            close(session, CloseStatus.SERVER_ERROR);
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
            // The connection is already unusable.
        }
    }
}