package io.kestra.runner.postgres;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.kestra.jdbc.runner.JdbcHeartbeat;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.extern.slf4j.Slf4j;

import java.net.UnknownHostException;
import java.util.function.Consumer;

@Slf4j
public class PostgresWorkerJobQueue implements WorkerJobQueueInterface {
    JdbcQueue<WorkerJob> workerTaskQueue;
    JdbcHeartbeat jdbcHeartbeat;
    AbstractJdbcWorkerJobRunningRepository jdbcWorkerJobRunningRepository;

    @SuppressWarnings("unchecked")
    public PostgresWorkerJobQueue(ApplicationContext applicationContext) {
        this.workerTaskQueue = (JdbcQueue<WorkerJob>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERJOB_NAMED)
        );
        this.jdbcHeartbeat = applicationContext.getBean(JdbcHeartbeat.class);
        this.jdbcWorkerJobRunningRepository = applicationContext.getBean(AbstractJdbcWorkerJobRunningRepository.class);

    }

    @Override
    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {
        return workerTaskQueue.receiveTransaction(consumerGroup, queueType, (dslContext, eithers) -> {

            eithers.forEach(either -> {
                if (either.isRight()) {
                    log.error("Unable to deserialize a worker job: {}", either.getRight().getMessage());
                    return;
                }
                WorkerJob workerJob = either.getLeft();
                WorkerInstance workerInstance = null;
                try {
                    workerInstance = jdbcHeartbeat.get();
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
                WorkerJobRunning workerJobRunning;

                if (workerJob instanceof WorkerTask workerTask) {
                    workerJobRunning = WorkerTaskRunning.of(
                        workerTask,
                        workerInstance,
                        0
                    );
                } else if (workerJob instanceof WorkerTrigger workerTrigger) {
                    workerJobRunning = WorkerTriggerRunning.of(
                        workerTrigger,
                        workerInstance,
                        0
                    );
                }
                else {
                    throw new IllegalArgumentException("Message is of type " + workerJob.getClass() + " which should never occurs");
                }

                jdbcWorkerJobRunningRepository.save(workerJobRunning, dslContext);

                log.trace("Sending a workerJobRunning: {}", workerJobRunning);
            });

            eithers.forEach(consumer);
        });
    }


    @Override
    public void pause() {

    }

    @Override
    public void close() {

    }
}
