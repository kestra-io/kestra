package io.kestra.repository.postgres;

import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcMcpSessionRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresMcpSessionRepository extends AbstractJdbcMcpSessionRepository {

    @Inject
    public PostgresMcpSessionRepository(@Named("mcpsession") PostgresRepository<McpSession> repository) {
        super(repository);
    }
}
