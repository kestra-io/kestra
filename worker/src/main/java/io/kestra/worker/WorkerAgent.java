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

import org.awaitility.Awaitility;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.server.AbstractService;
import io.kestra.core.server.Metric;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.utils.Disposable;
import io.kestra.core.worker.models.WorkerContext;
import io.kestra.worker.fetchers.WorkerJobFetcher;
import io.kestra.worker.senders.WorkerIOSender;
import io.kestra.worker.services.WorkerConnectionService;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.server.Service.ServiceState.TERMINATED_FORCED;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_GRACEFULLY;

@Slf4j
@Singleton
public class WorkerAgent extends AbstractService implements Worker {

    private static final String SERVICE_PROPS_WORKER_GROUP = "worker.group";

    private final MetricRegistry metricRegistry;

    private final ServerConfig serverConfig;

    private final AtomicBoolean skipGracefulTermination = new AtomicBoolean(false);

    private final WorkerConnectionService workerConnectionService;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final WorkerJobExecutor workerJobExecutor;
    private final WorkerJobFetcher workerJobFetcher;

    private final List<WorkerIOSender> workerIOSenders;
    private final ExecutorService workerIOThreadsExecutor;

    private final MaintenanceService maintenanceService;

    private String workerGroup;

    private final List<Disposable> disposables = new ArrayList<>();

    /**
     * Creates a new {@link WorkerAgent} instance.
     */
    @Inject
    public WorkerAgent(
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        WorkerConnectionService workerConnectionService,
        WorkerJobExecutor workerJobExecutor,
        WorkerJobFetcher workerJobFetcher,
        List<WorkerIOSender> workerIOSenders,
        MaintenanceService maintenanceService,
        MetricRegistry metricRegistry,
        ServerConfig serverConfig) {
        super(ServiceType.WORKER, eventPublisher);
        this.workerConnectionService = workerConnectionService;
        this.workerJobExecutor = workerJobExecutor;
        this.workerJobFetcher = workerJobFetcher;
        this.workerIOSenders = workerIOSenders;
        this.maintenanceService = maintenanceService;
        this.workerIOThreadsExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("worker-io-", 0).factory());
        this.metricRegistry = metricRegistry;
        this.serverConfig = serverConfig;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Set<Metric> getMetrics() {
        if (this.metricRegistry == null) {
            // can arrive if called before the instance is fully created
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

    /**
     * {@inheritDoc}
     **/
    @Override
    public void start(int numThreads, String workerGroupKey) {
        if (!this.initialized.compareAndSet(false, true)) {
            throw new IllegalStateException("Worker already started");
        }

        // Connect to the controller to resolve worker configuration
        WorkerConnectionService.ConnectionResult connectionResult = workerConnectionService.connect(getId(), workerGroupKey);
        this.workerGroup = connectionResult.workerGroup();

        this.setState(ServiceState.CREATED);

        String[] tags = workerGroup == null ? new String[0] : new String[] { MetricRegistry.TAG_WORKER_GROUP, workerGroup };
        // create metrics to store thread count, pending jobs and running jobs, so we can have autoscaling easily
        this.metricRegistry.gauge(MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT, MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT_DESCRIPTION, numThreads, tags);

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

        // Initialize and start all WorkerIO threads
        workerIOSenders.forEach(sender -> sender.init(workerContext));
        workerIOSenders.forEach(workerIOThreadsExecutor::submit);

        // Start the WorkerJobExecutor
        workerJobExecutor.start(workerContext);

        boolean inMaintenanceMode = maintenanceService.isInMaintenanceMode();
        if (inMaintenanceMode) {
            // If the worker is in maintenance mode, we don't want to start fetching new jobs
            workerJobFetcher.pause();
        }

        setState(inMaintenanceMode ? ServiceState.MAINTENANCE : ServiceState.RUNNING);

        // Start the WorkerJobFetcher
        workerJobFetcher.init(workerContext);
        workerIOThreadsExecutor.submit(workerJobFetcher);

        if (workerGroupKey != null) {
            log.info("Worker started with {} thread(s) in group '{}'", numThreads, workerGroupKey);
        } else {
            log.info("Worker started with {} thread(s)", numThreads);
        }
    }

    private void enterMaintenance() {
        this.workerJobFetcher.pause();
        this.setState(ServiceState.MAINTENANCE);
    }

    private void exitMaintenance() {
        this.workerJobFetcher.resume();
        this.setState(ServiceState.RUNNING);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<WorkerJob> getRunningJobs() {
        return workerJobExecutor.getRunningJobs();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected Map<String, Object> getProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SERVICE_PROPS_WORKER_GROUP, workerGroup);
        return properties;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected ServiceState doStop() {
        disposables.forEach(Disposable::dispose);

        // Stop fetching new WorkerJob
        this.workerJobFetcher.stop(Duration.ZERO);

        // Pause the WorkerJobExecutor to avoid starting new jobs while stopping
        this.workerJobExecutor.pause();

        // Wait for WorkerJob completion
        final boolean terminatedGracefully;
        if (!skipGracefulTermination.get()) {
            terminatedGracefully = waitForJobsCompletion(serverConfig.terminationGracePeriod());
        } else {
            log.info("Terminating now and skip waiting for job completions.");
            this.workerJobExecutor.shutdownNow();
            terminatedGracefully = false;
        }
        // Stop all Worker IO Sender Threads
        stopAllWorkerIOThreads();
        return terminatedGracefully ? TERMINATED_GRACEFULLY : TERMINATED_FORCED;
    }

    /**
     * Stop the worker immediately, without waiting for job completion.
     * <p>
     * This method exits for testing purposes, as it may lead to data loss or inconsistent state if jobs are terminated abruptly.
     */
    public void stopNow() {
        log.info("Stopping now");
        this.disposables.forEach(Disposable::dispose);

        // Stop fetching new WorkerJob
        this.workerJobFetcher.stop(Duration.ZERO);

        // Stop all Worker IO Sender Threads
        stopAllWorkerIOThreads();

        // Stop WorkerJobExecutor
        this.workerJobExecutor.shutdownNow();
        log.info("Stopped");
    }

    private void stopAllWorkerIOThreads() {
        // Stop all Worker IO Sender Threads
        workerIOSenders.forEach(WorkerIOSender::stop);

        // Shutdown the WorkerIO threads executor and wait for termination
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
        // Initiate shutdown
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

        // Wait for jobs completion
        Awaitility.await()
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
     *
     * @param skipGracefulTermination {@code true} to skip graceful termination on shutdown.
     */
    @Override
    public void skipGracefulTermination(final boolean skipGracefulTermination) {
        this.skipGracefulTermination.set(skipGracefulTermination);
    }
}
