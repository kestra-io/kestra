package io.kestra.repository.postgres;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueService;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Date;

@Singleton
@PostgresRepositoryEnabled
public class PostgresTriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public PostgresTriggerRepository(@Named("triggers") PostgresRepository<Trigger> repository,
                                     QueueService queueService,
                                     JdbcFilterService filterService) {
        super(repository, queueService, filterService);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return PostgresRepositoryUtils.formatDateField(dateField, groupType);
    }
}
