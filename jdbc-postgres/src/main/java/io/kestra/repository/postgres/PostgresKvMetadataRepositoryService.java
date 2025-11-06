package io.kestra.repository.postgres;

import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;

public abstract class PostgresKvMetadataRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<PersistedKvMetadata> jdbcRepository, String query) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }
}
