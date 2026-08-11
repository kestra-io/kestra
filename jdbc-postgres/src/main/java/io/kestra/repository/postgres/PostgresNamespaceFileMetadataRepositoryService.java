package io.kestra.repository.postgres;

import java.util.ArrayList;
import java.util.List;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.jdbc.AbstractJdbcRepository;

public abstract class PostgresNamespaceFileMetadataRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<NamespaceFileMetadata> jdbcRepository, String query) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }
}
