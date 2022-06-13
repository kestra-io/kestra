package io.kestra.repository.h2;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.jdbc.repository.AbstractLogRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;

@Singleton
@H2RepositoryEnabled
public class H2LogRepository extends AbstractLogRepository {
    @Inject
    public H2LogRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(LogEntry.class, applicationContext));
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }
}

