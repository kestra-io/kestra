package io.kestra.repository.postgres;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.jdbc.repository.AbstractLogRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.stream.Collectors;


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

    @Override
    protected Condition minLevel(Level minLevel) {
        return DSL.condition("level in (" +
            LogEntry
                .findLevelsByMin(minLevel)
                .stream()
                .map(s -> "'" + s + "'::log_level")
                .collect(Collectors.joining(", ")) +
            ")");
    }
}
