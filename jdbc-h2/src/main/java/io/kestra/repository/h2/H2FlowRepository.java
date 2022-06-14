package io.kestra.repository.h2;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;

@Singleton
@H2RepositoryEnabled
public class H2FlowRepository extends AbstractJdbcFlowRepository {
    @Inject
    public H2FlowRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(Flow.class, applicationContext), applicationContext);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }

    @Override
    protected Condition findSourceCodeCondition(String query) {
        return this.jdbcRepository.fullTextCondition(List.of("source_code"), query);
    }
}
