package io.kestra.runner.postgres;

import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.jdbc.runner.AbstractJdbcConcurrencyLimitStorage;
import io.kestra.repository.postgres.PostgresRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresQueueEnabled
public class PostgresConcurrencyLimitStorage extends AbstractJdbcConcurrencyLimitStorage {
    public PostgresConcurrencyLimitStorage(@Named("concurrencylimit") PostgresRepository<ConcurrencyLimit> repository) {
        super(repository);
    }
}
