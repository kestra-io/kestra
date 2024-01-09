package io.kestra.runner.h2;

import io.kestra.core.runners.SubflowExecution;
import io.kestra.jdbc.runner.AbstractJdbcSubflowExecutionStorage;
import io.kestra.repository.h2.H2Repository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2SubflowExecutionStorage extends AbstractJdbcSubflowExecutionStorage {
    public H2SubflowExecutionStorage(ApplicationContext applicationContext) {
        super(new H2Repository<>(SubflowExecution.class, applicationContext));
    }
}
