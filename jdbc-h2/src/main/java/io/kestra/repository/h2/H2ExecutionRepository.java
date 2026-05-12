package io.kestra.repository.h2;

import java.util.*;

import org.jooq.Condition;
import org.jooq.Field;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.event.ApplicationEventPublisher;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2ExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public H2ExecutionRepository(@Named("executions") H2Repository<Execution> repository,
                                 ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher,
                                 SystemFlowsConfiguration systemFlowsConfiguration,
                                 JdbcFilterService filterService) {
        super(repository, eventPublisher, systemFlowsConfiguration, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return H2ExecutionRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    public Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        return H2ExecutionRepositoryService.findLabelCondition(input, operation);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return H2RepositoryUtils.formatDateField(dateField, groupType);
    }
}
