package io.kestra.repository.mysql;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;

public abstract class MysqlKvMetadataRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<PersistedKvMetadata> jdbcRepository, String query) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("name"), query));
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }
}
