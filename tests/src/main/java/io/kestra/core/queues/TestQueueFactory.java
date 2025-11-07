package io.kestra.core.queues;

import io.kestra.core.models.executions.Execution;
import io.micronaut.context.annotation.*;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.lang.reflect.Proxy;
import java.util.*;

@Factory
@Requires(bean = QueueFactoryInterface.class)
public class TestQueueFactory {
    public static final InheritableThreadLocal<List<Execution>> testExecutions = new InheritableThreadLocal<>();

    private QueueInterface<Execution> delegate;

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
                    Arrays.stream(args).filter(arg -> arg instanceof Execution).forEach(arg -> {
                        synchronized (testExecutions.get()) {
                            testExecutions.get().add((Execution) arg);
                        }
                    });
                }
                return method.invoke(this.delegate, args);
            } catch (Exception e) {
                throw Optional.ofNullable(e.getCause()).orElse(e);
            }
        });
    }
}
