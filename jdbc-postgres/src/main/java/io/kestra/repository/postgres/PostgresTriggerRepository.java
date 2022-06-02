package io.kestra.repository.postgres;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.jdbc.repository.AbstractTriggerRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresTriggerRepository extends AbstractTriggerRepository implements TriggerRepositoryInterface {
    @Inject
    public PostgresTriggerRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(Trigger.class, applicationContext));
    }
}
