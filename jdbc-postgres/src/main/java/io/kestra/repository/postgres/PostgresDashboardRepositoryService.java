package io.kestra.repository.postgres;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.jdbc.AbstractJdbcRepository;

public abstract class PostgresDashboardRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Dashboard> jdbcRepository, String query) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
        }

        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }
}
