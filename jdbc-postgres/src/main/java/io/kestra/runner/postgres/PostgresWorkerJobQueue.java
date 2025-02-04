package io.kestra.runner.postgres;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.JdbcWorkerJobQueueService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class PostgresWorkerJobQueue implements WorkerJobQueueInterface {
    private final JdbcWorkerJobQueueService jdbcWorkerJobQueueService;

    public PostgresWorkerJobQueue(ApplicationContext applicationContext) {
        this.jdbcWorkerJobQueueService = applicationContext.getBean(JdbcWorkerJobQueueService.class);
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {
        return jdbcWorkerJobQueueService.receive(consumerGroup, queueType, consumer);
    }

    @Override
    public void close() {
        jdbcWorkerJobQueueService.close();
    }

    @Override
    public void pause() {
        jdbcWorkerJobQueueService.pause();
    }

    @Override
    public void resume() {
        jdbcWorkerJobQueueService.resume();
    }
}
