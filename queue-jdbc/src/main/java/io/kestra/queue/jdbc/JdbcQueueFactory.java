package io.kestra.queue.jdbc;

import io.kestra.queue.*;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Context
@JdbcQueueEnabled
public class JdbcQueueFactory {
    @Inject
    private QueueService queueService;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private JdbcQueueClient jdbcQueueClient;

    @PostConstruct
    void init() {
        QueueFactory
            .listAllEvent(this.getClass().getClassLoader(), DispatchEvent.class)
            .forEach(event -> applicationContext.registerSingleton(
                DispatchQueueInterface.class,
                new JdbcDispatchQueue<>(event, queueService, jdbcQueueClient),
                Qualifiers.byTypeArguments(event),
                true
            ));

        QueueFactory
            .listAllEvent(this.getClass().getClassLoader(), KeyedDispatchEvent.class)
            .forEach(event -> applicationContext.registerSingleton(
                KeyedDispatchQueueInterface.class,
                new JdbcKeyedDispatchQueue<>(event, queueService, jdbcQueueClient),
                Qualifiers.byTypeArguments(event),
                true
            ));

        QueueFactory
            .listAllEvent(this.getClass().getClassLoader(), BroadcastEvent.class)
            .forEach(event -> applicationContext.registerSingleton(
                BroadcastQueueInterface.class,
                new JdbcBroadcastQueue<>(event, queueService, jdbcQueueClient),
                Qualifiers.byTypeArguments(event),
                true
            ));

        QueueFactory
            .listAllEvent(this.getClass().getClassLoader(), VNodeDispatchEvent.class)
            .forEach(event -> applicationContext.registerSingleton(
                VNodeDispatchQueueInterface.class,
                new JdbcVNodeDispatchQueue<>(event, queueService, jdbcQueueClient),
                Qualifiers.byTypeArguments(event),
                true
            ));
    }
}
