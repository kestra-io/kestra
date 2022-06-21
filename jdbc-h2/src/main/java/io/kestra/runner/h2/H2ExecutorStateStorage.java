package io.kestra.runner.h2;

import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.core.runners.ExecutorState;
import io.kestra.repository.h2.H2Repository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2ExecutorStateStorage extends AbstractJdbcExecutorStateStorage {
    public H2ExecutorStateStorage(ApplicationContext applicationContext) {
        super(new H2Repository<>(ExecutorState.class, applicationContext));
    }
}
