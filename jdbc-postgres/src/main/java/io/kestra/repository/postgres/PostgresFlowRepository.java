package io.kestra.repository.postgres;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Map;

@Singleton
@PostgresRepositoryEnabled
public class PostgresFlowRepository extends AbstractJdbcFlowRepository {
    @Inject
    public PostgresFlowRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(Flow.class, applicationContext), applicationContext);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return PostgresFlowRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    protected Condition findSourceCodeCondition(String query) {
        return PostgresFlowRepositoryService.findSourceCodeCondition(this.jdbcRepository, query);
    }
}
