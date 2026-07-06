package io.kestra.repository.mysql;

import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcExecutionStatisticsRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlExecutionStatisticsRepository extends AbstractJdbcExecutionStatisticsRepository {
    @Inject
    public MysqlExecutionStatisticsRepository(@Named("executionstatistics") MysqlRepository<ExecutionStatistic> repository) {
        super(repository);
    }
}
