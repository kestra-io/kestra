package io.kestra.repository.postgres;

import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcCheckpointRepository;

import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresCheckpointRepository extends AbstractJdbcCheckpointRepository {
    public PostgresCheckpointRepository(@Named("checkpoints") PostgresRepository<Checkpoint> jdbcRepository) {
        super(jdbcRepository);
    }
}
