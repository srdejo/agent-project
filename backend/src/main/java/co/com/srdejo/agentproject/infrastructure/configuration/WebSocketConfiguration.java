package co.com.srdejo.agentproject.infrastructure.configuration;

import co.com.srdejo.agentproject.infrastructure.input.websocket.AgentPresenceService;
import co.com.srdejo.agentproject.infrastructure.input.websocket.AgentWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Configuration
public class WebSocketConfiguration {

    @Bean
    public HandlerMapping agentWebSocketMapping(AgentPresenceService presence, JsonMapper jsonMapper,
                                                @Value("${agent-project.agent.token:}") String token) {
        // Before the annotated controllers, so /ws/agent never falls through to them.
        return new SimpleUrlHandlerMapping(Map.of("/ws/agent", new AgentWebSocketHandler(presence, jsonMapper, token)),
                -1);
    }
}
