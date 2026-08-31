package io.kestra.queue.jdbc;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueService;
import io.kestra.core.queues.factory.QueueBackendDependencies;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.jdbc.runner.JdbcQueueEnabled;
import io.kestra.queue.jdbc.client.JdbcQueueClient;

import io.micronaut.context.annotation.Primary;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@JdbcQueueEnabled
@Singleton
@Primary
public record JdbcDependencies(@Inject QueueService queueService,
    @Inject JdbcQueueClient jdbcQueueClient,
    @Inject ExecutorsUtils executorsUtils,
    @Inject MetricRegistry metricRegistry,
    @Inject IgnoreExecutionService ignoreExecutionService) implements QueueBackendDependencies {
}
