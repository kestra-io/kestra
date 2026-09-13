package io.kestra.repository.mysql;

import io.kestra.core.models.executions.ExecutionOutput;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcExecutionOutputRepository;

import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlExecutionOutputRepository extends AbstractJdbcExecutionOutputRepository {
    public MysqlExecutionOutputRepository(@Named("executionoutputs") MysqlRepository<ExecutionOutput> jdbcRepository) {
        super(jdbcRepository);
    }
}
