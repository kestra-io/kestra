package io.kestra.repository.h2;

import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcMcpSessionRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2McpSessionRepository extends AbstractJdbcMcpSessionRepository {

    @Inject
    public H2McpSessionRepository(@Named("mcpsession") H2Repository<McpSession> repository) {
        super(repository);
    }
}
