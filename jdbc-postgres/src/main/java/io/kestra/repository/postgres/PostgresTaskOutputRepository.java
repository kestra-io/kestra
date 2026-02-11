package io.kestra.repository.postgres;

import io.kestra.core.models.executions.TaskOutput;
import io.kestra.jdbc.repository.AbstractJdbcTaskOutputRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresTaskOutputRepository extends AbstractJdbcTaskOutputRepository {
    public PostgresTaskOutputRepository(@Named("taskoutputs") PostgresRepository<TaskOutput> jdbcRepository) {
        super(jdbcRepository);
    }
}
