package io.kestra.webserver.services.ai.agent.store;

import java.util.Optional;
import java.util.function.UnaryOperator;

import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;

public interface ThreadStore {
    AgentThread create(AgentThread thread);

    Optional<AgentThread> find(String tenant, String uid);

    boolean exists(String tenant, String uid);

    AgentThread save(AgentThread thread);

    Optional<AgentThread> updateIf(String tenant, String uid, AgentThreadStatus expected, UnaryOperator<AgentThread> mutation);
}
