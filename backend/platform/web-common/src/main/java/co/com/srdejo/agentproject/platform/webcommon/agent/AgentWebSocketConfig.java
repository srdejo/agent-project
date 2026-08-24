package co.com.srdejo.agentproject.platform.webcommon.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSocket
public class AgentWebSocketConfig implements WebSocketConfigurer {
    private final AgentPresenceService presence;
    private final ObjectMapper objectMapper;
    private final String token;

    public AgentWebSocketConfig(AgentPresenceService presence, ObjectMapper objectMapper,
                                @Value("${agent-project.agent.token:}") String token) {
        this.presence = presence;
        this.objectMapper = objectMapper;
        this.token = token;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new AgentHandler(presence, objectMapper), "/ws/agent")
                .addInterceptors(new AgentTokenInterceptor(token))
                .setAllowedOriginPatterns("*");
    }

    private static final class AgentHandler extends TextWebSocketHandler {
        private final AgentPresenceService presence;
        private final ObjectMapper objectMapper;

        private AgentHandler(AgentPresenceService presence, ObjectMapper objectMapper) {
            this.presence = presence;
            this.objectMapper = objectMapper;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            if (Boolean.TRUE.equals(session.getAttributes().get("agent"))) {
                presence.registerAgent(session);
            } else {
                presence.registerDashboard(session);
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            if (!Boolean.TRUE.equals(session.getAttributes().get("agent"))) {
                return;
            }
            JsonNode payload = objectMapper.readTree(message.getPayload());
            presence.receiveAgentMessage(payload);
            if ("ping".equals(payload.path("type").asText())) {
                session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
            presence.remove(session);
        }
    }

    private static final class AgentTokenInterceptor implements HandshakeInterceptor {
        private final String expectedToken;

        private AgentTokenInterceptor(String expectedToken) {
            this.expectedToken = expectedToken;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler handler, Map<String, Object> attributes) {
            String role = queryValue(request, "role");
            if (!"agent".equals(role)) {
                return true;
            }
            String suppliedToken = queryValue(request, "token");
            boolean valid = !expectedToken.isBlank() && expectedToken.equals(suppliedToken);
            if (valid) {
                attributes.put("agent", true);
            }
            return valid;
        }

        private String queryValue(ServerHttpRequest request, String name) {
            return request.getURI().getQuery() == null ? "" :
                    java.util.Arrays.stream(request.getURI().getQuery().split("&"))
                            .map(pair -> pair.split("=", 2))
                            .filter(pair -> pair.length == 2 && name.equals(pair[0]))
                            .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                            .findFirst().orElse("");
        }

        @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                              WebSocketHandler handler, Exception exception) { }
    }
}