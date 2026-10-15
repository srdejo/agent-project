package co.com.srdejo.agentproject.infrastructure.input.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * {@code /ws/agent}: a client with {@code role=agent} must bring the shared token and is the only one whose messages
 * are read; any other client is a read-only dashboard.
 */
@Slf4j
public class AgentWebSocketHandler implements WebSocketHandler {

    private final AgentPresenceService presence;
    private final JsonMapper mapper;
    private final String token;

    public AgentWebSocketHandler(AgentPresenceService presence, JsonMapper mapper, String token) {
        this.presence = presence;
        this.mapper = mapper;
        this.token = token;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        var params = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri()).build().getQueryParams();
        if (!"agent".equals(params.getFirst("role"))) {
            return session.send(presence.dashboardStream().map(session::textMessage))
                    .and(session.receive().then());
        }
        String supplied = params.getFirst("token") == null ? ""
                : URLDecoder.decode(params.getFirst("token"), StandardCharsets.UTF_8);
        if (token.isBlank() || !token.equals(supplied)) {
            log.warn("Rejected agent connection: invalid token");
            return session.close(CloseStatus.POLICY_VIOLATION);
        }

        String id = session.getId();
        Mono<Void> outbound = session.send(presence.registerAgent(id).map(session::textMessage));
        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(text -> onAgentMessage(id, text))
                .then();
        return Mono.zip(inbound, outbound).then().doFinally(signal -> presence.removeAgent(id));
    }

    private void onAgentMessage(String sessionId, String text) {
        try {
            JsonNode payload = mapper.readTree(text);
            presence.receiveAgentMessage(payload);
            if ("ping".equals(payload.path("type").asString(""))) {
                presence.sendToAgent(sessionId, "{\"type\":\"pong\"}");
            }
        } catch (RuntimeException e) {
            log.warn("Ignoring malformed agent message: {}", e.getMessage());
        }
    }
}
