package io.kestra.repository.h2;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Map;

@Singleton
@H2RepositoryEnabled
public class H2FlowRepository extends AbstractJdbcFlowRepository {
    @Inject
    public H2FlowRepository(@Named("flows") H2Repository<FlowInterface> repository,
                            ModelValidator modelValidator,
                            ApplicationEventPublisher<CrudEvent<FlowInterface>> eventPublisher,
                            PluginDefaultService pluginDefaultService,
                            JdbcFilterService filterService) {
        super(repository, modelValidator, eventPublisher, pluginDefaultService, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return H2FlowRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    protected Condition findCondition(Object value, QueryFilter.Op operation) {
        return H2FlowRepositoryService.findCondition(value, operation);
    }


    @Override
    protected Condition findSourceCodeCondition(String query) {
        return H2FlowRepositoryService.findSourceCodeCondition(this.jdbcRepository, query);
    }
}
