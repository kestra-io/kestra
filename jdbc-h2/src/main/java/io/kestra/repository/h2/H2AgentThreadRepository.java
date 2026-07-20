package io.kestra.repository.h2;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAgentThreadRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2AgentThreadRepository extends AbstractJdbcAgentThreadRepository {

    @Inject
    public H2AgentThreadRepository(@Named("agentthread") H2Repository<AgentThread> repository) {
        super(repository);
    }
}
