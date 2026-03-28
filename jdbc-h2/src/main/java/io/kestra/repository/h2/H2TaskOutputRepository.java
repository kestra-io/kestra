package io.kestra.repository.h2;

import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcTaskOutputRepository;

import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2TaskOutputRepository extends AbstractJdbcTaskOutputRepository {
    public H2TaskOutputRepository(@Named("taskoutputs") H2Repository<TaskOutput> jdbcRepository) {
        super(jdbcRepository);
    }
}
