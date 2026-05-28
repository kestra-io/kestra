package io.kestra.repository.postgres;

import java.util.*;

import org.jooq.Condition;
import org.jooq.Field;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
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
@PostgresRepositoryEnabled
public class PostgresExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public PostgresExecutionRepository(@Named("executions") PostgresRepository<Execution> repository,
       ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher,
       SystemFlowsConfiguration systemFlowsConfiguration,
       JdbcFilterService filterService) {
        super(repository, eventPublisher, systemFlowsConfiguration, filterService);
    }

    @Override
    protected Condition statesFilter(List<State.Type> state) {
        return PostgresExecutionRepositoryService.statesFilter(state);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return PostgresExecutionRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    public Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        return PostgresExecutionRepositoryService.findLabelCondition(input, operation);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return PostgresRepositoryUtils.formatDateField(dateField, groupType);
    }
}
