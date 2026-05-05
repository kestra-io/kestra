package io.kestra.worker.services;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.kestra.core.reporter.ReportableRegistry;
import io.kestra.core.reporter.ReportableScheduler;
import io.kestra.core.reporter.ServerEventSender;
import io.kestra.core.reporter.UsageReportConfig;
import io.kestra.core.utils.ExecutorsUtils;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ReportableScheduler} for workers.
 * <p>
 * Unlike the standard {@link ReportableScheduler} which requires
 * {@code kestra.anonymous-usage-report.enabled=true}, this scheduler is always instantiated
 * on workers. Reporting is gated by {@link #init(UsageReportConfig)}, which is
 * called during the initial worker-to-controller connection based on the controller's configuration.
 * <p>
 * Scheduling is programmatic: no work is scheduled until the controller enables reporting.
 */
@Singleton
@Replaces(ReportableScheduler.class)
@Requires(property = "kestra.server-type", value = "WORKER")
@Slf4j
public class WorkerReportableScheduler extends ReportableScheduler {

    private final Object lock = new Object();
    private boolean enabled = false;
    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture<?> scheduledFuture;

    @Inject
    public WorkerReportableScheduler(ReportableRegistry registry, ServerEventSender sender) {
        super(registry, sender);
    }

    /**
     * Initializes reporting based on the given configuration.
     *
     * @param config the usage report configuration received from the controller.
     */
    public void init(UsageReportConfig config) {
        synchronized (lock) {
            if (this.enabled == config.enabled()) {
                return;
            }
            this.enabled = config.enabled();
            if (config.enabled()) {
                startScheduling(config.initialDelay(), config.fixedDelay());
            } else {
                stopScheduling();
            }
        }
    }

    private void startScheduling(Duration initialDelay, Duration fixedDelay) {
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "worker-reportable-scheduler")
            );
        }
        scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
            this::tick,
            initialDelay.toMillis(),
            fixedDelay.toMillis(),
            TimeUnit.MILLISECONDS
        );
        log.debug("Worker reportable scheduler started (initialDelay={}, fixedDelay={})", initialDelay, fixedDelay);
    }

    private void stopScheduling() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        log.debug("Worker reportable scheduler stopped");
    }

    @PreDestroy
    public void close() {
        synchronized (lock) {
            if (scheduledExecutorService != null) {
                ExecutorsUtils.closeScheduledThreadPool(
                    scheduledExecutorService,
                    Duration.ofSeconds(5),
                    scheduledFuture != null ? List.of(scheduledFuture) : List.of()
                );
                scheduledFuture = null;
                scheduledExecutorService = null;
            }
        }
    }
}
