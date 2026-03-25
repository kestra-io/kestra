package io.kestra.repository.postgres;

import io.kestra.core.lock.Lock;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.repository.AbstractJdbcLockRepository;

import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresLockRepository extends AbstractJdbcLockRepository {
    public PostgresLockRepository(@Named("locks") AbstractJdbcRepository<Lock> jdbcRepository) {
        super(jdbcRepository);
    }
}
