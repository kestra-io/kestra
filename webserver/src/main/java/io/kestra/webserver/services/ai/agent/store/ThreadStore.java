package io.kestra.webserver.services.ai.agent.store;

import java.util.Optional;

import io.kestra.webserver.services.ai.agent.domain.AgentThread;

public interface ThreadStore {
    AgentThread create(AgentThread thread);

    Optional<AgentThread> find(String tenant, String uid);

    boolean exists(String tenant, String uid);

    AgentThread save(AgentThread thread);
}
