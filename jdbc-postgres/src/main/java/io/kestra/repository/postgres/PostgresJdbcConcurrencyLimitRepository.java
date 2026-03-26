package io.kestra.repository.postgres;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.jdbc.repository.AbstractJdbcConcurrencyLimitRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresJdbcConcurrencyLimitRepository extends AbstractJdbcConcurrencyLimitRepository {
    @Inject
    public PostgresJdbcConcurrencyLimitRepository(@Named("concurrencylimit") PostgresRepository<ConcurrencyLimit> jdbcRepository) {
        super(jdbcRepository);
    }
}
