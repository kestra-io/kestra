package io.kestra.repository.postgres;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresTriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public PostgresTriggerRepository(@Named("triggers") PostgresRepository<Trigger> repository) {
        super(repository);
    }
}
