package io.kestra.repository.postgres;

import org.jooq.Condition;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.fethr.credential.Credential;
import io.kestra.jdbc.repository.AbstractJdbcCredentialRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * PostgreSQL persistence for {@link Credential}.
 *
 * <p>
 * PostgreSQL only, matching the fork: credentials were never built for H2, so they are absent from
 * a local {@code runLocal} instance.
 */
@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresCredentialRepository extends AbstractJdbcCredentialRepository {

    @Inject
    public PostgresCredentialRepository(@Named("credentials") PostgresRepository<Credential> repository) {
        super(repository);
    }

    @Override
    protected Condition findCondition(String query) {
        return PostgresCredentialRepositoryService.findCondition(this.jdbcRepository, query);
    }
}
