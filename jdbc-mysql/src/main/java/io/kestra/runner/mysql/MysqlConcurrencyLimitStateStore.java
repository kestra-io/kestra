package io.kestra.runner.mysql;

import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.jdbc.runner.AbstractJdbcConcurrencyLimitStateStore;
import io.kestra.repository.mysql.MysqlRepository;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlConcurrencyLimitStateStore extends AbstractJdbcConcurrencyLimitStateStore {
    public MysqlConcurrencyLimitStateStore(@Named("concurrencylimit") MysqlRepository<ConcurrencyLimit> repository) {
        super(repository);
    }
}
