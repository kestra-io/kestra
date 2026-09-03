package io.kestra.repository.postgres;

import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcExecutionStatisticsRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresExecutionStatisticsRepository extends AbstractJdbcExecutionStatisticsRepository {
    @Inject
    public PostgresExecutionStatisticsRepository(@Named("executionstatistics") PostgresRepository<ExecutionStatistic> repository) {
        super(repository);
    }
}
