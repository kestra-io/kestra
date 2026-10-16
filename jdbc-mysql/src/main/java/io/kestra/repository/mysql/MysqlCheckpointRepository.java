package io.kestra.repository.mysql;

import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcCheckpointRepository;

import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlCheckpointRepository extends AbstractJdbcCheckpointRepository {
    public MysqlCheckpointRepository(@Named("checkpoints") MysqlRepository<Checkpoint> jdbcRepository) {
        super(jdbcRepository);
    }
}
