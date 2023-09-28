package io.kestra.runner.postgres;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.JdbcPostgresWorkerJobQueueService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class PostgresWorkerJobQueue implements WorkerJobQueueInterface {
    private final JdbcPostgresWorkerJobQueueService jdbcPostgresWorkerJobQueueService;

    public PostgresWorkerJobQueue(ApplicationContext applicationContext) {
        this.jdbcPostgresWorkerJobQueueService = applicationContext.getBean(JdbcPostgresWorkerJobQueueService.class);
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {
        return jdbcPostgresWorkerJobQueueService.receive(consumerGroup, queueType, consumer);
    }

    @Override
    public void pause() {
        jdbcPostgresWorkerJobQueueService.pause();
    }

    @Override
    public void cleanup() {
        jdbcPostgresWorkerJobQueueService.cleanup();
    }

    @Override
    public void close() {
        jdbcPostgresWorkerJobQueueService.close();
    }
}
