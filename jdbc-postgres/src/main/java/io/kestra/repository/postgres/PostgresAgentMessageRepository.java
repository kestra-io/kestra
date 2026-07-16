package io.kestra.repository.postgres;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAgentMessageRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresAgentMessageRepository extends AbstractJdbcAgentMessageRepository {

    @Inject
    public PostgresAgentMessageRepository(@Named("agentmessage") PostgresRepository<AgentMessage> repository) {
        super(repository);
    }
}
