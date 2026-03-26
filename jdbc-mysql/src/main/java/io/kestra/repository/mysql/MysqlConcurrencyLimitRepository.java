package io.kestra.repository.mysql;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.jdbc.repository.AbstractJdbcConcurrencyLimitRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlConcurrencyLimitRepository extends AbstractJdbcConcurrencyLimitRepository {
    @Inject
    public MysqlConcurrencyLimitRepository(@Named("concurrencylimit") MysqlRepository<ConcurrencyLimit> jdbcRepository) {
        super(jdbcRepository);
    }
}
