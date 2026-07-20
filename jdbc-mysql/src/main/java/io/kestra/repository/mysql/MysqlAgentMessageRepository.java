package io.kestra.repository.mysql;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAgentMessageRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlAgentMessageRepository extends AbstractJdbcAgentMessageRepository {

    @Inject
    public MysqlAgentMessageRepository(@Named("agentmessage") MysqlRepository<AgentMessage> repository) {
        super(repository);
    }
}
