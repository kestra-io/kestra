package io.kestra.worker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.worker.fetchers.WorkerJobFetcher;
import io.kestra.worker.processors.WorkerJobProcessor;
import io.kestra.worker.processors.WorkerJobProcessorFactory;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.queues.WorkerQueueRegistry;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Components responsible for executing {@link io.kestra.core.runners.WorkerJob}s.
 * <p>
 * Bound as {@link Prototype} so each agent (the regular {@link WorkerAgent}, the
 * {@code SystemWorker}, ...) receives its own dedicated executor instance with
 * its own {@link io.kestra.core.worker.models.WorkerContext}.
 */
@Prototype
@Slf4j
public class WorkerJobExecutor {

    private static final String EXECUTOR_NAME = "worker";

    private final WorkerQueueRegistry workerQueueRegistry;
    private final WorkerJobProcessorFactory workerJobProcessorFactory;
    private final ExecutorsUtils executorsUtils;
    private final MetricRegistry metricRegistry;
    private final WorkerJobFetcher workerJobFetcher;

    private ExecutorService executorService;
    private List<WorkerJobConsumer> workerJobConsumers;
    private List<Thread> consumerThreads;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final AtomicInteger pendingJobCount = new AtomicInteger(0);
    private final AtomicInteger runningJobCount = new AtomicInteger(0);

    @Inject
    public WorkerJobExecutor(final WorkerQueueRegistry workerQueueRegistry,
        final ExecutorsUtils executorsUtils,
        final WorkerJobProcessorFactory workerJobProcessorFactory,
        final MetricRegistry metricRegistry,
        final WorkerJobFetcher workerJobFetcher) {
        this.workerJobProcessorFactory = workerJobProcessorFactory;
        this.workerQueueRegistry = workerQueueRegistry;
        this.executorsUtils = executorsUtils;
        this.metricRegistry = metricRegistry;
        this.workerJobFetcher = workerJobFetcher;
    }

    public void start(final io.kestra.core.worker.models.WorkerContext context) {
        WorkerQueue<WorkerJob> workerJobQueue = workerQueueRegistry.getOrCreate(context, WorkerJob.class);
        if (this.started.compareAndSet(false, true)) {
            // Thread pool for task and trigger execution
            this.executorService = executorsUtils.maxCachedThreadPool(context.workerThreads(), EXECUTOR_NAME);
            this.workerJobConsumers = new ArrayList<>(context.workerThreads());
            this.consumerThreads = new ArrayList<>(context.workerThreads());
            for (int i = 0; i < context.workerThreads(); i++) {
                WorkerJobConsumer consumer = new WorkerJobConsumer(
                    i,
                    workerJobQueue,
                    workerJobProcessorFactory,
                    context,
                    executorService,
                    workerJobFetcher
                );
                this.workerJobConsumers.add(consumer);
                // Consumers on virtual threads — they only poll and wait
                this.consumerThreads.add(
                    Thread.ofVirtual()
                        .name("worker-consumer-" + i)
                        .start(consumer)
                );
            }

            // create metrics for pending and running job counts
            String[] tags = metricRegistry.workerGroupTags(context.workerGroupId());
            this.metricRegistry.gauge(MetricRegistry.METRIC_WORKER_PENDING_COUNT, MetricRegistry.METRIC_WORKER_PENDING_COUNT_DESCRIPTION, pendingJobCount, tags);
            this.metricRegistry.gauge(MetricRegistry.METRIC_WORKER_RUNNING_COUNT, MetricRegistry.METRIC_WORKER_RUNNING_COUNT_DESCRIPTION, runningJobCount, tags);
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

        // Stop consumers from polling new jobs
        this.workerJobConsumers.forEach(consumer -> consumer.stop(Duration.ZERO));

        if (terminationGracePeriod == null || terminationGracePeriod.equals(Duration.ZERO)) {
            // Force: kill in-flight tasks, then unblock consumers
            this.executorService.shutdownNow();
            this.consumerThreads.forEach(Thread::interrupt);
            return false;
        }

        // Graceful: wait for consumers to finish (they exit once the in-flight task completes)
        long remaining = terminationGracePeriod.toMillis();
        for (Thread consumerThread : this.consumerThreads) {
            long start = System.nanoTime();
            consumerThread.join(remaining);
            remaining -= TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (remaining <= 0) {
                break;
            }
        }

        // Now safe to shut down the executor — no more submissions possible
        this.executorService.shutdown();
        boolean terminated = remaining > 0 && this.executorService.awaitTermination(remaining, TimeUnit.MILLISECONDS);

        if (!terminated) {
            log.warn("Worker still has pending jobs after the termination grace period. Forcing shutdown.");
            this.executorService.shutdownNow();
            this.consumerThreads.forEach(Thread::interrupt);
        }

        return terminated;
    }

    /**
     * A {@link WorkerJobConsumer} is responsible for continuously polling
     * for new {@link WorkerJob} and processing them sequentially.
     */
    private class WorkerJobConsumer extends WorkerLoop {

        private final AtomicReference<WorkerJobProcessor<WorkerJob>> running = new AtomicReference<>(null);
        private final AtomicReference<WorkerJob> workerJob = new AtomicReference<>(null);

        private final WorkerQueue<WorkerJob> workerJobQueue;
        private final WorkerJobProcessorFactory workerJobProcessorFactory;
        private final io.kestra.core.worker.models.WorkerContext workerContext;
        private final ExecutorService taskExecutorService;
        private final WorkerJobFetcher workerJobFetcher;

        public WorkerJobConsumer(int index,
            WorkerQueue<WorkerJob> workerJobQueue,
            WorkerJobProcessorFactory workerJobProcessorFactory,
            io.kestra.core.worker.models.WorkerContext workerContext,
            ExecutorService taskExecutorService,
            WorkerJobFetcher workerJobFetcher) {
            super("WorkerJobConsumer-" + index);
            this.workerJobQueue = workerJobQueue;
            this.workerJobProcessorFactory = workerJobProcessorFactory;
            this.workerContext = workerContext;
            this.taskExecutorService = taskExecutorService;
            this.workerJobFetcher = workerJobFetcher;
        }

        /**
         * Polls for new {@link WorkerJob} and processes them sequentially.
         * <p>
         * It blocks while waiting for new jobs and ensures that only one job is processed
         * at a time. Tasks are submitted to the platform thread pool for execution,
         * providing interrupt isolation between the consumer and the task.
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
                pendingJobCount.incrementAndGet();
                WorkerJobProcessor<WorkerJob> processor = workerJobProcessorFactory.create(workerContext, job);
                running.set(processor);
                workerJob.set(job);

                // Submit the task to the thread pool; consumer waits
                Future<?> future = taskExecutorService.submit(() ->
                {
                    pendingJobCount.decrementAndGet();
                    runningJobCount.incrementAndGet();
                    try {
                        processor.process(job);
                    } finally {
                        runningJobCount.decrementAndGet();
                    }
                });
                future.get();
            } catch (ExecutionException | RejectedExecutionException e) {
                // Task exception is fully contained — consumer never fails from task errors
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.error("Error while processing job '{}'", job.uid(), cause);
            } finally {
                running.set(null);
                workerJob.set(null);
                // Signal the owning controller that this job has reached a terminal
                // state on the worker, so the per-queue bucket slot reserved at
                // dispatch is released. The WorkerTaskResult itself still travels
                // via the dedicated Sender — this is just the capacity-accounting
                // signal piggy-backed on the bidi stream.
                workerJobFetcher.onJobCompleted(job.uid());
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
