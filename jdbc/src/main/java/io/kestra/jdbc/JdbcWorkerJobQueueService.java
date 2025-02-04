package io.kestra.jdbc;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.Pauseable;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceRegistry;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Singleton
@Slf4j
public class JdbcWorkerJobQueueService implements Closeable, Pauseable {
    private final JdbcQueue<WorkerJob> workerTaskQueue;
    private final AbstractJdbcWorkerJobRunningRepository jdbcWorkerJobRunningRepository;
    private final ServiceRegistry serviceRegistry;
    private final AtomicReference<Runnable> disposable = new AtomicReference<>();
    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    @SuppressWarnings("unchecked")
    public JdbcWorkerJobQueueService(ApplicationContext applicationContext) {
        this.workerTaskQueue = (JdbcQueue<WorkerJob>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERJOB_NAMED)
        );
        this.serviceRegistry = applicationContext.getBean(ServiceRegistry.class);
        this.jdbcWorkerJobRunningRepository = applicationContext.getBean(AbstractJdbcWorkerJobRunningRepository.class);
    }

    public Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<WorkerJob, DeserializationException>> consumer) {

        this.disposable.set(workerTaskQueue.receiveTransaction(consumerGroup, queueType, (dslContext, eithers) -> {

            Worker worker = serviceRegistry.waitForServiceAndGet(Service.ServiceType.WORKER).unwrap();

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

    @Override
    public void pause() {
        this.workerTaskQueue.pause();
    }

    @Override
    public void resume() {
        this.workerTaskQueue.resume();
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
