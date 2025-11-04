package io.kestra.repository.mysql;

import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.jdbc.repository.AbstractJdbcConcurrencyLimitRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlConcurrencyLimitRepository extends AbstractJdbcConcurrencyLimitRepository {
    @Inject
    public MysqlConcurrencyLimitRepository(@Named("concurrencylimit") MysqlRepository<ConcurrencyLimit> jdbcRepository) {
        super(jdbcRepository);
    }
}
