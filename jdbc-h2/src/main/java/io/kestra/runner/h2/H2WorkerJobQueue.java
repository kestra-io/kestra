package io.kestra.runner.h2;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.JdbcPostgresWorkerJobQueueService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class H2WorkerJobQueue implements WorkerJobQueueInterface {
    private final JdbcPostgresWorkerJobQueueService jdbcPostgresWorkerJobQueueService;

    public H2WorkerJobQueue(ApplicationContext applicationContext) {
        this.jdbcPostgresWorkerJobQueueService = applicationContext.getBean(JdbcPostgresWorkerJobQueueService.class);
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {
        return jdbcPostgresWorkerJobQueueService.receive(consumerGroup, queueType, consumer);
    }

    @Override
    public void pause() {

    }

    @Override
    public void close() {

    }
}
