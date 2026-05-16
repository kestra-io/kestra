package io.kestra.worker;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.server.AbstractService;
import io.kestra.core.server.Metric;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.Disposable;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.fetchers.JobFetcher;
import io.kestra.worker.senders.WorkerIOSender;

import io.micronaut.context.event.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.server.Service.ServiceState.TERMINATED_FORCED;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_GRACEFULLY;

/**
 * Common base class for worker-like services that:
 * <ul>
 *   <li>fetch worker jobs through a {@link JobFetcher} (gRPC pull or direct
 *       queue subscription);</li>
 *   <li>execute them through a per-instance {@link WorkerJobExecutor};</li>
 *   <li>forward emitted events (results, logs, metrics) through a list of
 *       {@link WorkerIOSender}s (gRPC senders or direct-queue senders);</li>
 *   <li>react to maintenance mode by pausing / resuming the fetcher.</li>
 * </ul>
 * <p>
 * Subclasses customize <em>which</em> fetcher and which IO senders are used,
 * and how the effective {@code workerGroup} for the {@link WorkerContext} is
 * resolved (gRPC handshake for {@link WorkerAgent}, a fixed reserved key for
 * the {@code SystemWorker}). Everything else is shared.
 */
@Slf4j
public abstract class AbstractWorker extends AbstractService {

    protected static final String SERVICE_PROPS_WORKER_GROUP = "worker.group";

    protected final MetricRegistry metricRegistry;
    protected final ServerConfig serverConfig;
    protected final MaintenanceService maintenanceService;

    protected final WorkerJobExecutor workerJobExecutor;
    protected final JobFetcher jobFetcher;
    protected final List<WorkerIOSender> workerIOSenders;
    protected final ExecutorService workerIOThreadsExecutor;

    protected final AtomicBoolean initialized = new AtomicBoolean(false);
    protected final AtomicBoolean skipGracefulTermination = new AtomicBoolean(false);

    protected final List<Disposable> disposables = new ArrayList<>();

    protected String workerGroup;

    protected AbstractWorker(
        final ServiceType serviceType,
        final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        final WorkerJobExecutor workerJobExecutor,
        final JobFetcher jobFetcher,
        final List<? extends WorkerIOSender> workerIOSenders,
        final MaintenanceService maintenanceService,
        final MetricRegistry metricRegistry,
        final ServerConfig serverConfig,
        final String ioThreadNamePrefix
    ) {
        super(serviceType, eventPublisher);
        this.workerJobExecutor = workerJobExecutor;
        this.jobFetcher = jobFetcher;
        this.workerIOSenders = List.copyOf(workerIOSenders);
        this.maintenanceService = maintenanceService;
        this.metricRegistry = metricRegistry;
        this.serverConfig = serverConfig;
        this.workerIOThreadsExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name(ioThreadNamePrefix, 0).factory()
        );
    }

    /**
     * Resolve the worker group used to build the {@link WorkerContext}.
     * <p>
     * Called once during {@link #start(int, String)}. Subclasses may perform
     * a gRPC handshake here (regular worker) or simply return a fixed string
     * (SystemWorker).
     *
     * @param workerGroupKey the worker group key passed by the caller; may
     *                       be {@code null}.
     * @return the resolved worker group; {@code null} for the default group.
     */
    protected abstract String resolveWorkerGroup(String workerGroupKey);

    /**
     * Starts the worker.
     */
    public void start(int numThreads, String workerGroupKey) {
        if (!this.initialized.compareAndSet(false, true)) {
            throw new IllegalStateException("Worker already started");
        }

        this.workerGroup = resolveWorkerGroup(workerGroupKey);

        this.setState(ServiceState.CREATED);

        String[] tags = workerGroup == null ? new String[0] : new String[] { MetricRegistry.TAG_WORKER_GROUP, workerGroup };
        this.metricRegistry.gauge(
            MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT,
            MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT_DESCRIPTION,
            numThreads,
            tags
        );

        WorkerContext workerContext = new WorkerContext(getId(), workerGroup, numThreads);

        disposables.add(maintenanceService.listen(new MaintenanceService.MaintenanceListener() {
            @Override
            public void onMaintenanceModeEnter() {
                enterMaintenance();
            }

            @Override
            public void onMaintenanceModeExit() {
                exitMaintenance();
            }
        }));

        // Initialize and start all WorkerIO senders
        workerIOSenders.forEach(sender -> sender.init(workerContext));
        workerIOSenders.forEach(workerIOThreadsExecutor::submit);

        workerJobExecutor.start(workerContext);

        boolean inMaintenanceMode = maintenanceService.isInMaintenanceMode();
        if (inMaintenanceMode) {
            // In maintenance mode, do not fetch new jobs but accept the ones already in flight
            jobFetcher.pause();
        }

        setState(inMaintenanceMode ? ServiceState.MAINTENANCE : ServiceState.RUNNING);

        jobFetcher.init(workerContext);
        workerIOThreadsExecutor.submit(jobFetcher);

        if (workerGroup != null) {
            log.info("Worker started with {} thread(s) in group '{}'", numThreads, workerGroup);
        } else {
            log.info("Worker started with {} thread(s)", numThreads);
        }
    }

    private void enterMaintenance() {
        this.jobFetcher.pause();
        this.setState(ServiceState.MAINTENANCE);
    }

    private void exitMaintenance() {
        this.jobFetcher.resume();
        this.setState(ServiceState.RUNNING);
    }

    /**
     * Gets the list of jobs currently running.
     */
    public List<WorkerJob> getRunningJobs() {
        return workerJobExecutor.getRunningJobs();
    }

    @Override
    public Set<Metric> getMetrics() {
        if (this.metricRegistry == null) {
            return Collections.emptySet();
        }

        Stream<String> metrics = Stream.of(
            MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT,
            MetricRegistry.METRIC_WORKER_RUNNING_COUNT
        );

        return metrics
            .flatMap(metric -> Optional.ofNullable(metricRegistry.findGauge(metric)).stream())
            .map(Metric::of)
            .collect(Collectors.toSet());
    }

    @Override
    protected Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SERVICE_PROPS_WORKER_GROUP, workerGroup);
        return properties;
    }

    @Override
    protected ServiceState doStop() {
        disposables.forEach(Disposable::dispose);

        // Stop fetching new jobs
        this.jobFetcher.stop(Duration.ZERO);

        // Pause the executor so no new jobs are started while we wind down
        this.workerJobExecutor.pause();

        final boolean terminatedGracefully;
        if (!skipGracefulTermination.get()) {
            terminatedGracefully = waitForJobsCompletion(serverConfig.terminationGracePeriod());
        } else {
            log.info("Terminating now and skip waiting for job completions.");
            this.workerJobExecutor.shutdownNow();
            terminatedGracefully = false;
        }

        stopAllWorkerIOThreads();
        return terminatedGracefully ? TERMINATED_GRACEFULLY : TERMINATED_FORCED;
    }

    /**
     * Stop the worker immediately, without waiting for job completion.
     */
    public void stopNow() {
        log.info("Stopping now");
        this.disposables.forEach(Disposable::dispose);

        this.jobFetcher.stop(Duration.ZERO);
        stopAllWorkerIOThreads();
        this.workerJobExecutor.shutdownNow();
        log.info("Stopped");
    }

    private void stopAllWorkerIOThreads() {
        workerIOSenders.forEach(WorkerIOSender::stop);

        workerIOThreadsExecutor.shutdown();
        try {
            if (!workerIOThreadsExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("WorkerIO senders did not terminate gracefully, forcing shutdown");
                workerIOThreadsExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for Worker IO thread termination", e);
            workerIOThreadsExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private boolean waitForJobsCompletion(final Duration timeout) {
        final Instant deadline = Instant.now().plus(timeout);
        final AtomicReference<ServiceState> shutdownState = new AtomicReference<>();

        Thread.ofVirtual().name("worker-shutdown").start(
            () ->
            {
                try {
                    long remaining = Math.max(0, Instant.now().until(deadline, ChronoUnit.MILLIS));
                    boolean gracefullyShutdown = this.workerJobExecutor.shutdown(Duration.ofMillis(remaining));
                    shutdownState.set(gracefullyShutdown ? TERMINATED_GRACEFULLY : TERMINATED_FORCED);
                } catch (InterruptedException e) {
                    log.error("Failed to shutdown. Thread was interrupted");
                    shutdownState.set(TERMINATED_FORCED);
                }
            }
        );

        Await.await()
            .pollInterval(Duration.ofSeconds(1))
            .ignoreExceptions()
            .until(() ->
            {
                ServiceState serviceState = shutdownState.get();
                if (serviceState == TERMINATED_FORCED || serviceState == TERMINATED_GRACEFULLY) {
                    log.info("All worker jobs are terminated");
                    return true;
                }

                long runningJobs = this.workerJobExecutor.getRunningJobCount();
                if (runningJobs == 0) {
                    log.info("All worker threads are terminated");
                } else {
                    log.warn("Waiting for all worker job to terminate (remaining: {}).", runningJobs);
                }
                return false;
            });

        return shutdownState.get() == TERMINATED_GRACEFULLY;
    }

    /**
     * Specify whether to skip graceful termination on shutdown.
     */
    public void skipGracefulTermination(final boolean skipGracefulTermination) {
        this.skipGracefulTermination.set(skipGracefulTermination);
    }
}
