package io.kestra.jdbc;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.runners.*;
import io.kestra.core.server.ServiceRegistry;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Singleton
@Slf4j
public class JdbcWorkerJobQueueService implements Closeable {
    private final AbstractJdbcWorkerJobRunningRepository jdbcWorkerJobRunningRepository;
    private final ServiceRegistry serviceRegistry;
    private final AtomicReference<Runnable> disposable = new AtomicReference<>();
    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    public JdbcWorkerJobQueueService(ApplicationContext applicationContext) {
        this.serviceRegistry = applicationContext.getBean(ServiceRegistry.class);
        this.jdbcWorkerJobRunningRepository = applicationContext.getBean(AbstractJdbcWorkerJobRunningRepository.class);
    }

    public Runnable receive(JdbcQueue<WorkerJob> workerJobQueue, String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {

        this.disposable.set(workerJobQueue.receiveTransaction(consumerGroup, queueType, (dslContext, eithers) -> {

            Worker worker = serviceRegistry.waitForServiceAndGet(ServiceType.WORKER).unwrap();

            final WorkerInstance workerInstance = new WorkerInstance(worker.getId(), worker.getWorkerGroup());

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
        }));

        return this.disposable.get();
    }

    /** {@inheritDoc} **/
    @Override
    public void close() {
        if (!isStopped.compareAndSet(true, false)) {
            return;
        }

        Runnable runnable = this.disposable.get();
        if (runnable != null) {
            runnable.run();
            this.disposable.set(null);
        }
    }
}
