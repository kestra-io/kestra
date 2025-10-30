package io.kestra.repository.mysql;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Map;

@Singleton
@MysqlRepositoryEnabled
public class MysqlFlowRepository extends AbstractJdbcFlowRepository {
    @Inject
    public MysqlFlowRepository(@Named("flows") MysqlRepository<FlowInterface> repository,
                               ApplicationContext applicationContext,
                               JdbcFilterService filterService) {
        super(repository, applicationContext, filterService);
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
}
