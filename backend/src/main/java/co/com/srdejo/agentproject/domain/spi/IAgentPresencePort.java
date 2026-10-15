package co.com.srdejo.agentproject.domain.spi;

public interface IAgentPresencePort {

    /** True only while the agent's own heartbeat keeps arriving: the API answering says nothing about the agent. */
    boolean isAgentOnline();
}
