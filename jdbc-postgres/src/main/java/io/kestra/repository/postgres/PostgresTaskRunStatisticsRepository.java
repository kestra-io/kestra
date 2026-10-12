package io.kestra.repository.postgres;

import io.kestra.core.models.tasks.TaskRunStatistic;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcTaskRunStatisticsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresTaskRunStatisticsRepository extends AbstractJdbcTaskRunStatisticsRepository {
    @Inject
    public PostgresTaskRunStatisticsRepository(@Named("taskrunstatistics") PostgresRepository<TaskRunStatistic> repository) {
        super(repository);
    }
}