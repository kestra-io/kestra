package io.kestra.repository.postgres;

import org.jooq.Condition;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.fethr.secret.Secret;
import io.kestra.jdbc.repository.AbstractJdbcSecretRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * PostgreSQL persistence for {@link Secret}.
 *
 * <p>
 * PostgreSQL only, matching the fork: the secrets feature was never built for H2, so a local
 * {@code runLocal} instance falls back to {@code SECRET_*} environment variables.
 */
@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresSecretRepository extends AbstractJdbcSecretRepository {

    @Inject
    public PostgresSecretRepository(@Named("secrets") PostgresRepository<Secret> repository) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return PostgresSecretRepositoryService.findCondition(query);
    }
}
