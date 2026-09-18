package io.kestra.repository.mysql;

import io.kestra.core.models.tasks.TaskRunStatistic;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcTaskRunStatisticsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlTaskRunStatisticsRepository extends AbstractJdbcTaskRunStatisticsRepository {
    @Inject
    public MysqlTaskRunStatisticsRepository(@Named("taskrunstatistics") MysqlRepository<TaskRunStatistic> repository) {
        super(repository);
    }
}