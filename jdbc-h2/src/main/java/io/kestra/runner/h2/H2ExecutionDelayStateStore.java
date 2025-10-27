package io.kestra.runner.h2;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractJdbcExecutionDelayStateStore;
import io.kestra.repository.h2.H2Repository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2ExecutionDelayStateStore extends AbstractJdbcExecutionDelayStateStore {
    public H2ExecutionDelayStateStore(@Named("executordelayed") H2Repository<ExecutionDelay> repository) {
        super(repository);
    }
}
