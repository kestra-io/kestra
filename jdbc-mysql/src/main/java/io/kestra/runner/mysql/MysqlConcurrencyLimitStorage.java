package io.kestra.runner.mysql;

import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.jdbc.runner.AbstractJdbcConcurrencyLimitStorage;
import io.kestra.repository.mysql.MysqlRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlConcurrencyLimitStorage extends AbstractJdbcConcurrencyLimitStorage {
    public MysqlConcurrencyLimitStorage(@Named("concurrencylimit") MysqlRepository<ConcurrencyLimit> repository) {
        super(repository);
    }
}
