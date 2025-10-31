package io.kestra.runner.postgres;

import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.jdbc.runner.AbstractJdbcConcurrencyLimitStateStore;
import io.kestra.repository.postgres.PostgresRepository;
import io.kestra.repository.postgres.PostgresRepositoryEnabled;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresConcurrencyLimitStateStore extends AbstractJdbcConcurrencyLimitStateStore {
    public PostgresConcurrencyLimitStateStore(@Named("concurrencylimit") PostgresRepository<ConcurrencyLimit> repository) {
        super(repository);
    }
}
