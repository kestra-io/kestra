package io.kestra.repository.mysql;

import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcTaskOutputRepository;

import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlTaskOutputRepository extends AbstractJdbcTaskOutputRepository {
    public MysqlTaskOutputRepository(@Named("taskoutputs") MysqlRepository<TaskOutput> jdbcRepository) {
        super(jdbcRepository);
    }
}
