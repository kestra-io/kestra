package io.kestra.repository.h2;

import io.kestra.core.models.executions.ExecutionOutput;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcExecutionOutputRepository;

import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2ExecutionOutputRepository extends AbstractJdbcExecutionOutputRepository {
    public H2ExecutionOutputRepository(@Named("executionoutputs") H2Repository<ExecutionOutput> jdbcRepository) {
        super(jdbcRepository);
    }
}
