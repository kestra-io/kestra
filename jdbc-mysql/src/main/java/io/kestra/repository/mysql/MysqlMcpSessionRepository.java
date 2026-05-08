package io.kestra.repository.mysql;

import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcMcpSessionRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlMcpSessionRepository extends AbstractJdbcMcpSessionRepository {

    @Inject
    public MysqlMcpSessionRepository(@Named("mcpsession") MysqlRepository<McpSession> repository) {
        super(repository);
    }
}
