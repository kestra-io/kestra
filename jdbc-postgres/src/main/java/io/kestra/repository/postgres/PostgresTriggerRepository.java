package io.kestra.repository.postgres;

import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.core.scheduler.model.TriggerState;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Field;

import java.util.Date;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresTriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public PostgresTriggerRepository(@Named("triggers") PostgresRepository<TriggerState> repository,
                                     JdbcFilterService filterService) {
        super(repository, filterService);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return PostgresRepositoryUtils.formatDateField(dateField, groupType);
    }
}
