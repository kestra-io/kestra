package io.kestra.repository.h2;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.jdbc.repository.AbstractTriggerRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2TriggerRepository extends AbstractTriggerRepository {
    @Inject
    public H2TriggerRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(Trigger.class, applicationContext));
    }
}
