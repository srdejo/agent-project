package co.com.srdejo.agentproject.infrastructure.out.agent;

import co.com.srdejo.agentproject.domain.spi.IAgentPresencePort;
import co.com.srdejo.agentproject.infrastructure.input.websocket.AgentPresenceService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AgentPresenceAdapter implements IAgentPresencePort {

    private final AgentPresenceService presence;

    @Override
    public boolean isAgentOnline() {
        return presence.isAgentOnline();
    }
}
