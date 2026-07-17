package io.kestra.repository.h2;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAgentMessageRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2AgentMessageRepository extends AbstractJdbcAgentMessageRepository {

    @Inject
    public H2AgentMessageRepository(@Named("agentmessage") H2Repository<AgentMessage> repository) {
        super(repository);
    }
}
