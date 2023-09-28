package io.kestra.runner.mysql;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.JdbcWorkerJobQueueService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class MysqlWorkerJobQueue implements WorkerJobQueueInterface {
    private final JdbcWorkerJobQueueService jdbcworkerjobQueueService;

    public MysqlWorkerJobQueue(ApplicationContext applicationContext) {
        this.jdbcworkerjobQueueService = applicationContext.getBean(JdbcWorkerJobQueueService.class);
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {
        return jdbcworkerjobQueueService.receive(consumerGroup, queueType, consumer);
    }

    @Override
    public void pause() {
        jdbcworkerjobQueueService.pause();
    }

    @Override
    public void cleanup() {
        jdbcworkerjobQueueService.cleanup();
    }

    @Override
    public void close() {
        jdbcworkerjobQueueService.close();
    }
}
