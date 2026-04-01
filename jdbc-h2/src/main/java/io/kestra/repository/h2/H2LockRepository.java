package io.kestra.repository.h2;

import io.kestra.core.lock.Lock;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.repository.AbstractJdbcLockRepository;

import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2LockRepository extends AbstractJdbcLockRepository {
    public H2LockRepository(@Named("locks") AbstractJdbcRepository<Lock> jdbcRepository) {
        super(jdbcRepository);
    }
}
