package io.kestra.repository.h2;

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
@H2RepositoryEnabled
public class H2TriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public H2TriggerRepository(@Named("triggers") H2Repository<Trigger> repository,
                               QueueService queueService,
                               JdbcFilterService filterService) {
        super(repository, queueService, filterService);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return H2RepositoryUtils.formatDateField(dateField, groupType);
    }
}
