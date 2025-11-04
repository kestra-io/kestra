package io.kestra.repository.h2;

import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.jdbc.repository.AbstractJdbcConcurrencyLimitRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2ConcurrencyLimitRepository extends AbstractJdbcConcurrencyLimitRepository {
    @Inject
    public H2ConcurrencyLimitRepository(@Named("concurrencylimit") H2Repository<ConcurrencyLimit> jdbcRepository) {
        super(jdbcRepository);
    }
}
