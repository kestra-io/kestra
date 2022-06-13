package io.kestra.runner.h2;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.jdbc.runner.AbstractExecutionDelayStorage;
import io.kestra.repository.h2.H2Repository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2lExecutionDelayStorage extends AbstractExecutionDelayStorage {
    public H2lExecutionDelayStorage(ApplicationContext applicationContext) {
        super(new H2Repository<>(ExecutionDelay.class, applicationContext));
    }
}
