package io.kestra.repository.h2;

import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcExecutionStatisticsRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2ExecutionStatisticsRepository extends AbstractJdbcExecutionStatisticsRepository {
    @Inject
    public H2ExecutionStatisticsRepository(@Named("executionstatistics") H2Repository<ExecutionStatistic> repository) {
        super(repository);
    }
}
