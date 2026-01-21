package io.kestra.queue.jdbc;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.queue.*;
import io.kestra.queue.jdbc.client.JdbcQueueClient;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Factory
@JdbcQueueEnabled
public class JdbcQueueFactory implements QueueFactoryInterface {
    @Inject
    private QueueService queueService;

    @Inject
    private JdbcQueueClient jdbcQueueClient;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Bean
    @Singleton
    @Override
    public DispatchQueueInterface<ExecutionCommand> executionCommandQueue() {
        return new JdbcDispatchQueue<>(ExecutionCommand.class, queueService, jdbcQueueClient, executorsUtils);
    }

    @Bean
    @Singleton
    @Override
    public BroadcastQueueInterface<ExecutionKilled> killQueue() {
        return new JdbcBroadcastQueue<>(ExecutionKilled.class, queueService, jdbcQueueClient, executorsUtils);
    }

    @Bean
    @Singleton
    @Override
    public DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue() {
        return new JdbcDispatchQueue<>(SubflowExecutionResult.class, queueService, jdbcQueueClient, executorsUtils);
    }

    @Bean
    @Singleton
    @Override
    public DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue() {
        return new JdbcDispatchQueue<>(SubflowExecutionEnd.class, queueService, jdbcQueueClient, executorsUtils);
    }

    @Bean
    @Singleton
    @Override
    public DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue() {
        return new JdbcDispatchQueue<>(MultipleConditionEvent.class, queueService, jdbcQueueClient, executorsUtils);
    }

    @Bean
    @Singleton
    @Override
    public DispatchQueueInterface<FlowInterface> flowQueue() {
        return new JdbcDispatchQueue<>(FlowInterface.class, queueService, jdbcQueueClient, executorsUtils);
    }
}
