package io.kestra.repository.postgres;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.kestra.fethr.credential.Credential;
import io.kestra.jdbc.AbstractJdbcRepository;

/**
 * The free-text search predicate for credentials on PostgreSQL.
 *
 * <p>
 * Unlike secrets, the credentials table carries a {@code fulltext} tsvector generated from the name
 * and description, so this searches the index rather than doing a LIKE over the columns.
 */
public abstract class PostgresCredentialRepositoryService {

    private PostgresCredentialRepositoryService() {
    }

    public static Condition findCondition(AbstractJdbcRepository<Credential> jdbcRepository, String query) {
        if (query == null || query.isBlank()) {
            return DSL.noCondition();
        }

        return jdbcRepository.fullTextCondition(java.util.List.of("fulltext"), query);
    }
}
