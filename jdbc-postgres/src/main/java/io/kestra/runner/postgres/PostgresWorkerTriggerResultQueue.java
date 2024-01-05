package io.kestra.runner.postgres;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.WorkerTriggerResultQueueInterface;
import io.kestra.core.runners.WorkerTriggerResult;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.JdbcWorkerTriggerResultQueueService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.function.Consumer;

@Slf4j
public class PostgresWorkerTriggerResultQueue extends PostgresQueue<WorkerTriggerResult> implements WorkerTriggerResultQueueInterface {
    private final JdbcWorkerTriggerResultQueueService jdbcWorkerTriggerResultQueueService;

    public PostgresWorkerTriggerResultQueue(ApplicationContext applicationContext) {
        super(WorkerTriggerResult.class, applicationContext);
        this.jdbcWorkerTriggerResultQueueService = applicationContext.getBean(JdbcWorkerTriggerResultQueueService.class);
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerTriggerResult, DeserializationException>> consumer) {
        return jdbcWorkerTriggerResultQueueService.receive(consumerGroup, queueType, consumer);
    }

    @Override
    public void pause() {
        jdbcWorkerTriggerResultQueueService.pause();
    }

    @Override
    public void cleanup() {
        jdbcWorkerTriggerResultQueueService.cleanup();
    }

    @Override
    public void close() throws IOException {
        super.close();
        jdbcWorkerTriggerResultQueueService.close();
    }
}
