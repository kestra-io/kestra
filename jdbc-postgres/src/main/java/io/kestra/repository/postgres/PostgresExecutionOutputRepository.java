package io.kestra.repository.postgres;

import io.kestra.core.models.executions.ExecutionOutput;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcExecutionOutputRepository;

import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresExecutionOutputRepository extends AbstractJdbcExecutionOutputRepository {
    public PostgresExecutionOutputRepository(@Named("executionoutputs") PostgresRepository<ExecutionOutput> jdbcRepository) {
        super(jdbcRepository);
    }
}
