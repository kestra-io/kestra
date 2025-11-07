package io.kestra.core.queues;

import io.kestra.core.models.executions.Execution;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Factory
@Requires(bean = QueueFactoryInterface.class)
public class TestQueueFactory {
    private QueueInterface<Execution> delegate;
    @Getter
    private List<Execution> testExecutions = new ArrayList<>();

    public TestQueueFactory(QueueFactoryInterface queueFactoryInterface) {
        this.delegate = queueFactoryInterface.execution();
    }

    @SuppressWarnings("unchecked")
    @Singleton
    @Replaces(named = QueueFactoryInterface.EXECUTION_NAMED)
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    @Bean(preDestroy = "close")
    public QueueInterface<Execution> execution() {
        return (QueueInterface<Execution>) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{QueueInterface.class}, (proxy, method, args) -> {
            try {
                if (method.getName().contains("emit")) {
                    Arrays.stream(args).filter(arg -> arg instanceof Execution).forEach(arg -> testExecutions.add((Execution) arg));
                }
                return method.invoke(this.delegate, args);
            } catch (Exception e) {
                throw Optional.ofNullable(e.getCause()).orElse(e);
            }
        });
    }
}
