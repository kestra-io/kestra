package io.kestra.runner.mysql;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.runners.WorkerTriggerResult;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.JdbcWorkerTriggerResultQueueService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * This specific queue is used to be able to purge WorkerJobRunning for triggers
 */
@Slf4j
public class MysqlWorkerTriggerResultQueue extends MysqlQueue<WorkerTriggerResult> {
    private final JdbcWorkerTriggerResultQueueService jdbcWorkerTriggerResultQueueService;

    public MysqlWorkerTriggerResultQueue(ApplicationContext applicationContext) {
        super(WorkerTriggerResult.class, applicationContext);
        this.jdbcWorkerTriggerResultQueueService = applicationContext.getBean(JdbcWorkerTriggerResultQueueService.class);
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerTriggerResult, DeserializationException>> consumer) {
        return jdbcWorkerTriggerResultQueueService.receive(this, consumerGroup, queueType, consumer);
    }

    @Override
    public void close() throws IOException {
        super.close();
        jdbcWorkerTriggerResultQueueService.close();
    }
}
