package io.kestra.runner.h2;

import io.kestra.jdbc.runner.AbstractExecutorStateStorage;
import io.kestra.jdbc.runner.JdbcExecutorState;
import io.kestra.repository.h2.H2Repository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2ExecutorStateStorage extends AbstractExecutorStateStorage {
    public H2ExecutorStateStorage(ApplicationContext applicationContext) {
        super(new H2Repository<>(JdbcExecutorState.class, applicationContext));
    }
}
