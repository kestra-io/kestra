package io.kestra.runner.h2;

import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.jdbc.runner.AbstractJdbcConcurrencyLimitStorage;
import io.kestra.repository.h2.H2Repository;
import io.kestra.repository.h2.H2RepositoryEnabled;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2ConcurrencyLimitStorage extends AbstractJdbcConcurrencyLimitStorage {
    public H2ConcurrencyLimitStorage(@Named("concurrencylimit") H2Repository<ConcurrencyLimit> repository) {
        super(repository);
    }
}
