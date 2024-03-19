package io.kestra.repository.mysql;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
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
    public MysqlFlowRepository(@Named("flows") MysqlRepository<Flow> repository,
                               ApplicationContext applicationContext) {
        super(repository, applicationContext);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return MysqlFlowRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    protected Condition findSourceCodeCondition(String query) {
        return MysqlFlowRepositoryService.findSourceCodeCondition(this.jdbcRepository, query);
    }
}
