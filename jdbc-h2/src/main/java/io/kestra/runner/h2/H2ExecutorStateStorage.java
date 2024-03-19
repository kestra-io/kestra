package io.kestra.runner.h2;

import io.kestra.core.runners.ExecutorState;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.repository.h2.H2Repository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2ExecutorStateStorage extends AbstractJdbcExecutorStateStorage {
    public H2ExecutorStateStorage(@Named("executorstate") H2Repository<ExecutorState> repository) {
        super(repository);
    }
}
