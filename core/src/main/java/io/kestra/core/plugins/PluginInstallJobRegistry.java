package io.kestra.core.plugins;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.docs.JsonSchemaCache;
import io.kestra.core.exceptions.KestraRuntimeException;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages async plugin installation jobs: {@link #submit(List)} enqueues a job and returns its id
 * immediately, callers poll {@link #get(UUID)} for progress, and terminal jobs are evicted after a
 * TTL. Thread pools are created lazily on the first submission, so an instance where auto-install
 * is never used allocates no threads.
 */
@Singleton
@Slf4j
public class PluginInstallJobRegistry {

    /** How long (seconds) to keep terminal jobs in memory after they finish, for UI polling. */
    private static final long TERMINAL_JOB_TTL_SECONDS = 3600L;

    /** A job stuck in RUNNING beyond this is cancelled so a stalled Maven resolve cannot pin a pool thread. */
    private static final Duration JOB_HARD_TIMEOUT = Duration.ofMinutes(5);

    /** Upper bound on pending + running jobs: the submission queue sits behind a public endpoint. */
    private static final int MAX_ACTIVE_JOBS = 16;

    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(5);

    private final ConcurrentHashMap<UUID, PluginInstallJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Future<?>> futures = new ConcurrentHashMap<>();
    // Dedup index: artifact-set key -> id of the non-terminal job already installing that exact set.
    private final ConcurrentHashMap<String, UUID> activeJobsByArtifacts = new ConcurrentHashMap<>();
    private final int concurrency;
    private final PluginManager pluginManager;
    private final JsonSchemaCache jsonSchemaCache;

    private volatile ExecutorService installExecutor;
    private volatile ScheduledExecutorService maintenanceExecutor;

    @Inject
    public PluginInstallJobRegistry(
        final PluginManager pluginManager,
        final JsonSchemaCache jsonSchemaCache,
        final PluginAutoInstallConfig config) {
        this(pluginManager, jsonSchemaCache, config.concurrency());
    }

    PluginInstallJobRegistry(
        final PluginManager pluginManager,
        final JsonSchemaCache jsonSchemaCache,
        final int concurrency) {
        this.pluginManager = Objects.requireNonNull(pluginManager);
        this.jsonSchemaCache = Objects.requireNonNull(jsonSchemaCache);
        this.concurrency = concurrency;
    }

    /**
     * Enqueues an installation of the given artifacts and returns the new job's id.
     * <p>
     * A submission covers a whole artifact set (one job per save, not per plugin), and is
     * deduplicated: while a job for the exact same set is still pending or running, its id is
     * returned instead of enqueuing a duplicate.
     *
     * @param artifacts the artifacts to install.
     * @return the {@link UUID} of the created (or already in-flight) {@link PluginInstallJob}.
     * @throws KestraRuntimeException if too many install jobs are already pending or running.
     */
    public UUID submit(final List<PluginArtifact> artifacts) {
        Objects.requireNonNull(artifacts, "artifacts must not be null");
        ensureStarted();

        return activeJobsByArtifacts.compute(artifactsKey(artifacts), (key, existingId) ->
        {
            if (existingId != null && get(existingId).filter(job -> !job.isTerminal()).isPresent()) {
                log.debug("Reusing in-flight plugin install job {} for artifacts: {}", existingId, artifacts);
                return existingId;
            }

            long active = jobs.values().stream().filter(job -> !job.isTerminal()).count();
            if (active >= MAX_ACTIVE_JOBS) {
                throw new KestraRuntimeException(
                    "Cannot queue a plugin install job: %d jobs are already pending or running and the limit is %d. Retry once the current installations finish."
                        .formatted(active, MAX_ACTIVE_JOBS)
                );
            }

            PluginInstallJob job = PluginInstallJob.pending(artifacts);
            jobs.put(job.id(), job);
            futures.put(job.id(), installExecutor.submit(() -> runInstall(job.id())));

            log.info("Queued async plugin install job {} for artifacts: {}", job.id(), artifacts);
            return job.id();
        });
    }

    /**
     * Blocks until the job with the given id reaches a terminal state, or the timeout elapses.
     * On timeout the job keeps running; the returned snapshot reflects its current state.
     *
     * @param jobId the job id.
     * @param timeout the maximum time to wait for the job to finish.
     * @return the job snapshot after waiting, or empty if the job is unknown.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    public Optional<PluginInstallJob> awaitTerminal(final UUID jobId, final Duration timeout) throws InterruptedException {
        Future<?> future = futures.get(jobId);
        if (future != null) {
            try {
                future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Timed out after {} while waiting for plugin install job {} to finish.", timeout, jobId);
            } catch (ExecutionException e) {
                // runInstall records its own failures on the job, so an execution failure here is unexpected.
                log.error("Plugin install job {} threw an unexpected error.", jobId, e.getCause());
            }
        }
        return get(jobId);
    }

    /**
     * Returns the current snapshot of the job with the given id, or empty if not found.
     *
     * @param jobId the job id.
     * @return the job snapshot, or empty.
     */
    public Optional<PluginInstallJob> get(final UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    // Lazily starts the pools on the first submission; double-checked so concurrent first callers race safely.
    private void ensureStarted() {
        if (installExecutor != null) {
            return;
        }
        synchronized (this) {
            if (installExecutor == null) {
                maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();
                maintenanceExecutor.scheduleAtFixedRate(this::evictTerminalJobs, 60, 60, TimeUnit.SECONDS);
                maintenanceExecutor.scheduleAtFixedRate(this::cancelStuckJobs, 60, 60, TimeUnit.SECONDS);
                // Installs are download-bound, so size from the allocated CPUs with a floor of 8.
                int poolSize = concurrency > 0
                    ? concurrency
                    : Math.max(8, 4 * KestraContext.getContext().getAllocatedCpuCores());
                installExecutor = Executors.newFixedThreadPool(poolSize);
            }
        }
    }

    private void runInstall(final UUID jobId) {
        PluginInstallJob job = jobs.compute(jobId, (id, current) -> current.running(Instant.now()));

        PluginInstallTransferListener listener = new PluginInstallTransferListener(job.progress());

        try {
            pluginManager.install(job.artifacts(), List.of(), true, null, listener);
            jsonSchemaCache.clear();
            jobs.compute(jobId, (id, current) -> current.succeeded(Instant.now()));
            log.info("Async plugin install job {} succeeded", jobId);
        } catch (Exception e) {
            log.error("Async plugin install job {} failed", jobId, e);
            jobs.compute(jobId, (id, current) -> current.failed(Instant.now(), e.getMessage()));
        }

        activeJobsByArtifacts.remove(artifactsKey(job.artifacts()), jobId);
    }

    private static String artifactsKey(final List<PluginArtifact> artifacts) {
        return artifacts.stream().map(PluginArtifact::toString).sorted().collect(Collectors.joining(","));
    }

    /** Fails and interrupts any job stuck in RUNNING beyond {@link #JOB_HARD_TIMEOUT}, so a stalled resolve cannot exhaust the pool. */
    private void cancelStuckJobs() {
        Instant cutoff = Instant.now().minus(JOB_HARD_TIMEOUT);
        jobs.forEach((jobId, job) ->
        {
            if (PluginInstallJob.Status.RUNNING == job.status() && job.startedAt() != null && job.startedAt().isBefore(cutoff)) {
                Optional.ofNullable(futures.get(jobId)).ifPresent(future -> future.cancel(true));
                jobs.compute(jobId, (id, current) ->
                    current.failed(Instant.now(), "Plugin install job was cancelled after exceeding the %s hard timeout.".formatted(JOB_HARD_TIMEOUT)));
                activeJobsByArtifacts.remove(artifactsKey(job.artifacts()), jobId);
                log.warn("Cancelled plugin install job {} stuck in RUNNING for more than {} (artifacts: {}).", jobId, JOB_HARD_TIMEOUT, job.artifacts());
            }
        });
    }

    // Terminal jobs stay queryable for TERMINAL_JOB_TTL_SECONDS (the UI toast polls after completion),
    // then this periodic sweep drops them.
    private void evictTerminalJobs() {
        Instant cutoff = Instant.now().minusSeconds(TERMINAL_JOB_TTL_SECONDS);
        jobs.entrySet().removeIf(entry ->
        {
            PluginInstallJob job = entry.getValue();
            return job.isTerminal() && job.finishedAt() != null && job.finishedAt().isBefore(cutoff);
        });
        futures.keySet().retainAll(jobs.keySet());
    }

    @PreDestroy
    public void shutdown() {
        // Two-step shutdown: let in-flight installs finish within a grace period before interrupting.
        shutdownGracefully(installExecutor);
        shutdownGracefully(maintenanceExecutor);
    }

    private static void shutdownGracefully(final ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
