package io.kestra.repository.postgres;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.fethr.table.TableDefinition;
import io.kestra.jdbc.repository.AbstractJdbcTableRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * PostgreSQL persistence for the table registry.
 *
 * <p>
 * PostgreSQL only, matching the fork: tables were never built for H2, so they are absent from a
 * local {@code runLocal} instance. That is more consequential here than for secrets or credentials,
 * because the feature creates real tables -- there is no environment-variable fallback to stand in.
 */
@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresTableRepository extends AbstractJdbcTableRepository {

    @Inject
    public PostgresTableRepository(@Named("tables") PostgresRepository<TableDefinition> repository) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        if (query == null || query.isBlank()) {
            return DSL.noCondition();
        }

        // The registry carries no fulltext column, so this matches the two columns a person searches by.
        String like = "%" + query + "%";
        return DSL.or(
            DSL.field("name").likeIgnoreCase(like),
            DSL.field("namespace").likeIgnoreCase(like)
        );
    }
}
