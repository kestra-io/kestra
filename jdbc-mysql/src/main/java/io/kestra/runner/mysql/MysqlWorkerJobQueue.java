package io.kestra.runner.mysql;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.JdbcWorkerJobQueueService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * This specific queue is used to be able to save WorkerJobRunning for each WorkerJob
 */
@Slf4j
public class MysqlWorkerJobQueue extends MysqlQueue<WorkerJob> {
    private final JdbcWorkerJobQueueService jdbcWorkerJobQueueService;

    public MysqlWorkerJobQueue(ApplicationContext applicationContext) {
        super(WorkerJob.class, applicationContext);
        this.jdbcWorkerJobQueueService = applicationContext.getBean(JdbcWorkerJobQueueService.class);
    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {
        return jdbcWorkerJobQueueService.receive(this, consumerGroup, queueType, consumer);
    }

    @Override
    public void close() throws IOException {
        super.close();
        jdbcWorkerJobQueueService.close();
    }
}
