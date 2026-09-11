package io.kestra.repository.h2;

import io.kestra.core.models.tasks.TaskRunStatistic;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcTaskRunStatisticsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2TaskRunStatisticsRepository extends AbstractJdbcTaskRunStatisticsRepository{

    @Inject
    public H2TaskRunStatisticsRepository(@Named("taskrunstatistics") H2Repository<TaskRunStatistic> repository) {
        super(repository);
    }

}