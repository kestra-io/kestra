package io.kestra.repository.postgres;

import java.util.Map;

import org.jooq.Condition;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.core.services.PluginDefaultService;
import io.micronaut.context.event.ApplicationEventPublisher;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresFlowRepository extends AbstractJdbcFlowRepository {
    @Inject
    public PostgresFlowRepository(@Named("flows") PostgresRepository<FlowInterface> repository,
          ModelValidator modelValidator,
          ApplicationEventPublisher<CrudEvent<FlowInterface>> eventPublisher,
          PluginDefaultService pluginDefaultService,
          JdbcFilterService filterService) {
        super(repository, modelValidator, eventPublisher, pluginDefaultService, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return PostgresFlowRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    protected Condition findCondition(Object value, QueryFilter.Op operation) {
        return PostgresFlowRepositoryService.findCondition(value, operation);
    }

    @Override
    protected Condition findSourceCodeCondition(String query) {
        return PostgresFlowRepositoryService.findSourceCodeCondition(this.jdbcRepository, query);
    }

    @Override
    protected Condition findTriggerClassCondition(Class<? extends io.kestra.core.models.triggers.AbstractTrigger> triggerClass) {
        return PostgresFlowRepositoryService.findTriggerClassCondition(triggerClass);
    }
}
