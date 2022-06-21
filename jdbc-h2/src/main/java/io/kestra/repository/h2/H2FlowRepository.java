package io.kestra.repository.h2;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
@H2RepositoryEnabled
public class H2FlowRepository extends AbstractJdbcFlowRepository {
    @Inject
    public H2FlowRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(Flow.class, applicationContext), applicationContext);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(this.jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        if (labels != null)  {
            labels.forEach((key, value) -> {
                Field<String> field = DSL.field("JQ_STRING(\"value\", '.labels." + key + "')", String.class);

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
        return this.jdbcRepository.fullTextCondition(List.of("source_code"), query);
    }
}
