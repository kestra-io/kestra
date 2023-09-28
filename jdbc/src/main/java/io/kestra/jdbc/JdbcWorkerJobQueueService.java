package io.kestra.jdbc;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.WorkerInstanceRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.kestra.jdbc.runner.JdbcHeartbeat;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Singleton
@Slf4j
public class JdbcWorkerJobQueueService {
    private final JdbcQueue<WorkerJob> workerTaskQueue;
    private final JdbcHeartbeat jdbcHeartbeat;
    private final AbstractJdbcWorkerJobRunningRepository jdbcWorkerJobRunningRepository;
    private final WorkerInstanceRepositoryInterface workerInstanceRepository;
    private Runnable queueStop;

    @SuppressWarnings("unchecked")
    public JdbcWorkerJobQueueService(ApplicationContext applicationContext) {
        this.workerTaskQueue = (JdbcQueue<WorkerJob>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERJOB_NAMED)
        );
        this.jdbcHeartbeat = applicationContext.getBean(JdbcHeartbeat.class);
        this.jdbcWorkerJobRunningRepository = applicationContext.getBean(AbstractJdbcWorkerJobRunningRepository.class);
        this.workerInstanceRepository = applicationContext.getBean(WorkerInstanceRepositoryInterface.class);
    }

    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {

        this.queueStop = workerTaskQueue.receiveTransaction(consumerGroup, queueType, (dslContext, eithers) -> {
            WorkerInstance workerInstance = jdbcHeartbeat.get();

            eithers.forEach(either -> {
                if (either.isRight()) {
                    log.error("Unable to deserialize a worker job: {}", either.getRight().getMessage());
                    return;
                }

                WorkerJob workerJob = either.getLeft();
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
                } else {
                    throw new IllegalArgumentException("Message is of type " + workerJob.getClass() + " which should never occurs");
                }

                jdbcWorkerJobRunningRepository.save(workerJobRunning, dslContext);

                if (log.isTraceEnabled()) {
                    log.trace("Sending a workerJobRunning: {}", workerJobRunning);
                }
            });

            eithers.forEach(consumer);
        });

        return this.queueStop;
    }

    public void pause() {
        this.stopQueue();
    }

    private void stopQueue() {
        synchronized (this) {
            if (this.queueStop != null) {
                this.queueStop.run();
                this.queueStop = null;
            }
        }
    }

    public void cleanup() {
        if (jdbcHeartbeat.get() != null) {
            synchronized (this) {
                workerInstanceRepository.delete(jdbcHeartbeat.get());
            }
        }
    }

    public void close() {
        this.stopQueue();
    }
}
