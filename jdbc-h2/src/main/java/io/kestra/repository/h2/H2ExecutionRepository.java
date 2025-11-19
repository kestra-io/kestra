package io.kestra.repository.h2;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueService;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.*;

@Singleton
@H2RepositoryEnabled
public class H2ExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public H2ExecutionRepository(@Named("executions") H2Repository<Execution> repository,
                                 QueueService queueService,
                                 ApplicationContext applicationContext,
                                 AbstractJdbcExecutorStateStorage executorStateStorage,
                                 JdbcFilterService filterService) {
        super(repository, queueService, applicationContext, executorStateStorage, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return H2ExecutionRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    protected Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        return H2ExecutionRepositoryService.findLabelCondition(input, operation);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return H2RepositoryUtils.formatDateField(dateField, groupType);    }
}