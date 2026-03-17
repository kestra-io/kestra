package io.kestra.queue.jdbc;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@JdbcQueueEnabled
@Singleton
public record JdbcDependencies (@Inject QueueService queueService,
                                @Inject JdbcQueueClient jdbcQueueClient,
                                @Inject ExecutorsUtils executorsUtils,
                                @Inject MetricRegistry metricRegistry,
                                @Inject IgnoreExecutionService ignoreExecutionService) {
}
