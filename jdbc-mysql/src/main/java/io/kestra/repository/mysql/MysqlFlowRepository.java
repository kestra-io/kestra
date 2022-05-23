package io.kestra.repository.mysql;

import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.repository.AbstractFlowRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Singleton
@MysqlRepositoryEnabled
public class MysqlFlowRepository extends AbstractFlowRepository {
    @Inject
    public MysqlFlowRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(Flow.class, applicationContext), applicationContext);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Arrays.asList("namespace", "id"), query);
    }

    @Override
    protected Condition findSourceCodeCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("source_code"), query);
    }
}
