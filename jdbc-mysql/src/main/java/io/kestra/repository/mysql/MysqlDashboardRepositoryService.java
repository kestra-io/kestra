package io.kestra.repository.mysql;

import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class MysqlDashboardRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Dashboard> jdbcRepository, String query) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Arrays.asList("title"), query));
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }
}
