package io.kestra.runner.h2;

import io.kestra.core.runners.ExecutionRunning;
import io.kestra.jdbc.runner.AbstractJdbcExecutionRunningStorage;
import io.kestra.repository.h2.H2Repository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2ExecutionRunningStorage extends AbstractJdbcExecutionRunningStorage {
    public H2ExecutionRunningStorage(@Named("executionrunning") H2Repository<ExecutionRunning> repository) {
        super(repository);
    }
}
