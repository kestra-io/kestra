package io.kestra.repository.postgres;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.jdbc.repository.AbstractLogRepository;
import io.kestra.jdbc.repository.AbstractTriggerRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Collections;

@Singleton
@PostgresRepositoryEnabled
public class PostgresLogRepository extends AbstractLogRepository implements LogRepositoryInterface {
    @Inject
    public PostgresLogRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(LogEntry.class, applicationContext));
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }
}
