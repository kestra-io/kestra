package io.kestra.repository.postgres;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
        }

        if (labels != null)  {
            labels.forEach((key, value) -> {
                Field<String> field = DSL.field("value #>> '{labels, " + key + "}'", String.class);

                if (value == null) {
                    conditions.add(field.isNotNull());
                } else {
                    conditions.add(field.eq(value));
                }
            });
        }

        return conditions.size() == 0 ? DSL.trueCondition() : DSL.and(conditions);
    }

    @Override
    protected Condition findSourceCodeCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("FULLTEXT_INDEX(source_code)"), query);
    }
}
