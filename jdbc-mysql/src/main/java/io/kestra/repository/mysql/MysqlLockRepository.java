package io.kestra.repository.mysql;

import io.kestra.core.lock.Lock;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.repository.AbstractJdbcLockRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlLockRepository extends AbstractJdbcLockRepository {
    public MysqlLockRepository(@Named("locks") AbstractJdbcRepository<Lock> jdbcRepository) {
        super(jdbcRepository);
    }
}
