package io.kestra.repository.h2;

import io.kestra.core.checkpoint.Checkpoint;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcCheckpointRepository;

import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2CheckpointRepository extends AbstractJdbcCheckpointRepository {
    public H2CheckpointRepository(@Named("checkpoints") H2Repository<Checkpoint> jdbcRepository) {
        super(jdbcRepository);
    }
}
