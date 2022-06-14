package io.kestra.repository.postgres;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.*;

import java.util.Collections;

@Singleton
@PostgresRepositoryEnabled
public class PostgresFlowRepository extends AbstractJdbcFlowRepository {
    @Inject
    public PostgresFlowRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(Flow.class, applicationContext), applicationContext);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }

    @Override
    protected Condition findSourceCodeCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("FULLTEXT_INDEX(source_code)"), query);
    }
}
