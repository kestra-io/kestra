package io.kestra.repository.mysql;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAgentThreadRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlAgentThreadRepository extends AbstractJdbcAgentThreadRepository {

    @Inject
    public MysqlAgentThreadRepository(@Named("agentthread") MysqlRepository<AgentThread> repository) {
        super(repository);
    }
}
