package io.kestra.repository.postgres;

import org.jooq.Condition;
import org.jooq.impl.DSL;

/**
 * The free-text search predicate for secrets on PostgreSQL.
 *
 * <p>
 * The secrets table carries no {@code fulltext} tsvector column -- a secret is found by its key,
 * namespace or description -- so this is a plain case-insensitive match rather than a
 * {@code FULLTEXT_SEARCH} over an indexed vector.
 */
public abstract class PostgresSecretRepositoryService {

    private PostgresSecretRepositoryService() {
    }

    public static Condition findCondition(String query) {
        if (query == null || query.isBlank()) {
            return DSL.noCondition();
        }

        String like = "%" + query + "%";
        return DSL.or(
            DSL.field("key").likeIgnoreCase(like),
            DSL.field("namespace").likeIgnoreCase(like),
            DSL.field("description").likeIgnoreCase(like)
        );
    }
}
