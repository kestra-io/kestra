package io.kestra.runner.h2;

import io.kestra.core.runners.ExecutionQueued;
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStateStore;
import io.kestra.repository.h2.H2Repository;
import io.kestra.repository.h2.H2RepositoryEnabled;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2ExecutionQueuedStateStore extends AbstractJdbcExecutionQueuedStateStore {
    public H2ExecutionQueuedStateStore(@Named("executionqueued") H2Repository<ExecutionQueued> repository) {
        super(repository);
    }
}
