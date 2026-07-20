package io.kestra.repository.postgres;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAgentThreadRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresAgentThreadRepository extends AbstractJdbcAgentThreadRepository {

    @Inject
    public PostgresAgentThreadRepository(@Named("agentthread") PostgresRepository<AgentThread> repository) {
        super(repository);
    }
}
