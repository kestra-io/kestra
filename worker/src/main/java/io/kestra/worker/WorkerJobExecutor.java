package io.kestra.worker;

import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.worker.processors.WorkerJobProcessor;
import io.kestra.worker.processors.WorkerJobProcessorFactory;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Components responsible for executing {@link io.kestra.core.runners.WorkerJob}s
 */
@Singleton
@Slf4j
public class WorkerJobExecutor {

    private static final String EXECUTOR_NAME = "worker";

    private final WorkerQueueRegistry workerQueueRegistry;
    private final WorkerJobProcessorFactory workerJobProcessorFactory;
    private final ExecutorsUtils executorsUtils;

    private ExecutorService executorService;
    private List<WorkerJobConsumer> workerJobConsumers;

    private final AtomicBoolean started = new AtomicBoolean(false);

    @Inject
    public WorkerJobExecutor(final WorkerQueueRegistry workerQueueRegistry,
                             final ExecutorsUtils executorsUtils,
                             final WorkerJobProcessorFactory workerJobProcessorFactory) {
        this.workerJobProcessorFactory = workerJobProcessorFactory;
        this.workerQueueRegistry = workerQueueRegistry;
        this.executorsUtils = executorsUtils;
    }

    public void start(final io.kestra.core.worker.models.WorkerContext context) {
        WorkerQueue<WorkerJob> workerJobQueue = workerQueueRegistry.getOrCreate(context, WorkerJob.class);
        if (this.started.compareAndSet(false, true)) {
            this.executorService = executorsUtils.maxCachedThreadPool(context.workerThreads(), EXECUTOR_NAME);
            this.workerJobConsumers = new ArrayList<>(context.workerThreads());
            for (int i = 0; i < context.workerThreads(); i++) {
                WorkerJobConsumer consumer = new WorkerJobConsumer(
                    i,
                    workerJobQueue,
                    workerJobProcessorFactory,
                    context
                );
                this.workerJobConsumers.add(consumer);
                this.executorService.submit(consumer);
            }
        } else {
            throw new IllegalStateException("already started");
        }
    }

    /**
     * Returns the number of running a job.
     *
     * @return the number of jobs being processed
     */
    public long getRunningJobCount() {
        return workerJobConsumers.stream()
            .filter(WorkerJobConsumer::isProcessing)
            .count();
    }

    /**
     * Gets the list of running jobs.
     *
     * @return the {@link WorkerJob}.
     */
    public List<WorkerJob> getRunningJobs() {
        return workerJobConsumers.stream()
            .map(WorkerJobConsumer::getWorkerJob)
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * Notify all underlying WorkerJob consumers to pause.
     */
    public void pause() {
        workerJobConsumers.forEach(WorkerJobConsumer::pause);
    }

    /**
     * Notify all underlying WorkerJob consumers to resume.
     */
    public void resume() {
        checkIsStarted();
        workerJobConsumers.forEach(WorkerJobConsumer::resume);
    }

    private void checkIsStarted() {
        if (!this.started.get()) {
            throw new IllegalStateException("WorkerJobExecutor not started");
        }
    }

    /**
     * Immediately initiates shutdown of all consumers and halts the processing of waiting jobs.
     * <p>
     * This is a convenience method that calls {@link #shutdown(Duration)} with {@code Duration.ZERO}
     * and ignores any {@link InterruptedException} by resetting the interrupt flag.
     */
    public void shutdownNow() {
        try {
            shutdown(Duration.ZERO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Initiates a graceful shutdown by notifying all consumers to stop and waiting for termination.
     * <p>
     * If the specified {@code terminationGracePeriod} is {@code null} or {@code Duration.ZERO},
     * the executor will skip graceful shutdown and immediately attempt to forcefully stop all
     * running tasks.
     *
     * @param terminationGracePeriod the maximum duration to wait for graceful shutdown
     * @return {@code true} if the executor terminated within the timeout; {@code false} if forced shutdown was required
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public boolean shutdown(final Duration terminationGracePeriod) throws InterruptedException {
        if (!this.started.compareAndSet(true, false)) {
            return true; // Already shut down or not started.
        }

        // Initiate a graceful shutdown
        this.executorService.shutdown();

        // Notify all WorkerJobConsumers to stop
        this.workerJobConsumers.forEach(consumer -> consumer.stop(Duration.ZERO));

        if (terminationGracePeriod == null || terminationGracePeriod.equals(Duration.ZERO)) {
            this.executorService.shutdownNow();
            return false;
        }

        // Wait for all WorkerJobConsumers to terminate
        boolean terminated = this.executorService.awaitTermination(
            terminationGracePeriod.toMillis(), TimeUnit.MILLISECONDS);

        if (!terminated) {
            log.warn("Worker still has pending jobs after the termination grace period. Forcing shutdown.");
            this.executorService.shutdownNow();
        }

        return terminated;
    }

    /**
     * A {@link WorkerJobConsumer} is responsible for continuously polling
     * for new {@link WorkerJob} and processing them sequentially.
     */
    private static class WorkerJobConsumer extends WorkerLoop {
        
        private final AtomicReference<WorkerJobProcessor<WorkerJob>> running = new AtomicReference<>(null);
        private final AtomicReference<WorkerJob> workerJob = new AtomicReference<>(null);

        private final WorkerQueue<WorkerJob> workerJobQueue;
        private final WorkerJobProcessorFactory workerJobProcessorFactory;
        private final io.kestra.core.worker.models.WorkerContext workerContext;

        public WorkerJobConsumer(int index,
                                 WorkerQueue<WorkerJob> workerJobQueue,
                                 WorkerJobProcessorFactory workerJobProcessorFactory,
                                 io.kestra.core.worker.models.WorkerContext workerContext) {
            super("WorkerJobConsumer-" + index);
            this.workerJobQueue = workerJobQueue;
            this.workerJobProcessorFactory = workerJobProcessorFactory;
            this.workerContext = workerContext;
        }

        /**
         * Polls for new {@link WorkerJob} and processes them sequentially.
         * <p>
         * It blocks while waiting for new jobs and ensures that only one job is processed
         * at a time. This method will not return unless interrupted or explicitly stopped.
         */
        @Override
        protected void doOnLoop() throws Exception {
            // Poll next Worker Job to process
            WorkerJob job = workerJobQueue.poll(Duration.ofSeconds(1));

            // Check if the consumer was stopped while polling
            if (job == null) {
                return;
            }

            try {
                WorkerJobProcessor<WorkerJob> processor = workerJobProcessorFactory.create(workerContext, job);
                running.set(processor);
                workerJob.set(job);
                processor.process(job);
            } finally {
                running.set(null);
                workerJob.set(null);
            }
        }

        /**
         * Check whether a job is currently being processed
         *
         * @return {@code true} if a {@link WorkerJob} is actively being processed; {@code false} otherwise.
         */
        public boolean isProcessing() {
            return running.get() != null;
        }

        public Optional<WorkerJob> getWorkerJob() {
            return Optional.ofNullable(workerJob.get());
        }

        /** {@inheritDoc} **/
        @Override
        protected void signalJobStop() {
            WorkerJobProcessor<WorkerJob> processor = running.get();
            if (processor != null) {
                processor.stop();
            }
        }
    }
}
