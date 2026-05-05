package io.kestra.repository.mysql;

import java.util.Map;

import org.jooq.Condition;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.event.ApplicationEventPublisher;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlFlowRepository extends AbstractJdbcFlowRepository {
    @Inject
    public MysqlFlowRepository(@Named("flows") MysqlRepository<FlowInterface> repository,
       ModelValidator modelValidator,
       ApplicationEventPublisher<CrudEvent<FlowInterface>> eventPublisher,
       PluginDefaultService pluginDefaultService,
       JdbcFilterService filterService) {
        super(repository, modelValidator, eventPublisher, pluginDefaultService, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return MysqlFlowRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    protected Condition findCondition(Object value, QueryFilter.Op operation) {
        return MysqlFlowRepositoryService.findCondition(value, operation);
    }

    @Override
    protected Condition findSourceCodeCondition(String query) {
        return MysqlFlowRepositoryService.findSourceCodeCondition(this.jdbcRepository, query);
    }

    @Override
    protected Condition findTriggerClassCondition(Class<? extends io.kestra.core.models.triggers.AbstractTrigger> triggerClass) {
        return MysqlFlowRepositoryService.findTriggerClassCondition(triggerClass);
    }
}
