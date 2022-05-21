package io.kestra.repository.mysql;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.jdbc.repository.AbstractTriggerRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlTriggerRepository extends AbstractTriggerRepository {
    @Inject
    public MysqlTriggerRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(Trigger.class, applicationContext));
    }}
