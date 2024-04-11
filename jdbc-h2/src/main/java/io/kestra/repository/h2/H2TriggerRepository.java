package io.kestra.repository.h2;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2TriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public H2TriggerRepository(@Named("triggers") H2Repository<Trigger> repository) {
        super(repository);
    }
}
