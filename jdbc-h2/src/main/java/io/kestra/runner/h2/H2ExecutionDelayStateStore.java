package io.kestra.runner.h2;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractJdbcExecutionDelayStateStore;
import io.kestra.repository.h2.H2Repository;
import io.kestra.repository.h2.H2RepositoryEnabled;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2ExecutionDelayStateStore extends AbstractJdbcExecutionDelayStateStore {
    public H2ExecutionDelayStateStore(@Named("executordelayed") H2Repository<ExecutionDelay> repository) {
        super(repository);
    }

    @Override
    protected Temporal getNow(Instant now) {
        // H2 stores 'date' as a naive TIMESTAMP holding UTC, so the bind must be a zone-less UTC wall clock (kestra-io/kestra#14056).
        return LocalDateTime.ofInstant(now, ZoneOffset.UTC);
    }
}
