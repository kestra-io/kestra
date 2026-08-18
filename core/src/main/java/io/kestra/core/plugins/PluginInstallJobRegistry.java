package io.kestra.core.plugins;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.kestra.core.docs.JsonSchemaCache;

import io.micronaut.context.annotation.Value;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages async plugin installation jobs.
 * <p>
 * Each call to {@link #submit(List)} enqueues an install job, returns its {@link UUID} immediately,
 * and executes the actual installation in a background thread pool. Callers poll
 * {@link #get(UUID)} to track progress. Completed jobs are evicted after a configurable TTL.
 */
@Singleton
@Slf4j
public class PluginInstallJobRegistry {

    /** How long (seconds) to keep terminal jobs in memory after they finish. */
    private static final long TERMINAL_JOB_TTL_SECONDS = 3600L;

    private final ConcurrentHashMap<UUID, AtomicReference<PluginInstallJob>> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Future<?>> futures = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor installExecutor;
    private final ScheduledExecutorService evictionExecutor;
    private final PluginManager pluginManager;
    private final JsonSchemaCache jsonSchemaCache;

    @Inject
    public PluginInstallJobRegistry(
        final PluginManager pluginManager,
        final JsonSchemaCache jsonSchemaCache,
        @Value("${kestra.plugins.auto-install.concurrency:2}") final int concurrency) {
        this.pluginManager = Objects.requireNonNull(pluginManager);
        this.jsonSchemaCache = Objects.requireNonNull(jsonSchemaCache);
        this.installExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(concurrency);
        this.evictionExecutor = Executors.newSingleThreadScheduledExecutor();
        this.evictionExecutor.scheduleAtFixedRate(this::evictTerminalJobs, 10, 60, TimeUnit.SECONDS);
    }

    /**
     * Enqueues an installation of the given artifacts and returns the new job's id.
     *
     * @param artifacts the artifacts to install.
     * @return the {@link UUID} of the created {@link PluginInstallJob}.
     */
    public UUID submit(final List<PluginArtifact> artifacts) {
        Objects.requireNonNull(artifacts, "artifacts must not be null");

        PluginInstallJob job = PluginInstallJob.pending(artifacts);
        AtomicReference<PluginInstallJob> ref = new AtomicReference<>(job);
        jobs.put(job.id(), ref);

        futures.put(job.id(), installExecutor.submit(() -> runInstall(ref)));

        log.info("Queued async plugin install job {} for artifacts: {}", job.id(), artifacts);
        return job.id();
    }

    /**
     * Blocks until the job with the given id reaches a terminal state, or the timeout elapses.
     * <p>
     * On timeout the job keeps running in the background; the returned snapshot simply reflects
     * its current, possibly non-terminal, state.
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
        AtomicReference<PluginInstallJob> ref = jobs.get(jobId);
        return Optional.ofNullable(ref).map(AtomicReference::get);
    }

    private void runInstall(final AtomicReference<PluginInstallJob> ref) {
        PluginInstallJob job = ref.get();
        ref.set(job.running(Instant.now()));
        job = ref.get();

        PluginInstallTransferListener listener = new PluginInstallTransferListener(job.progress());

        try {
            pluginManager.install(job.artifacts(), List.of(), true, null, listener);
            jsonSchemaCache.clear();
            ref.set(ref.get().succeeded(Instant.now()));
            log.info("Async plugin install job {} succeeded", job.id());
        } catch (Exception e) {
            log.error("Async plugin install job {} failed", job.id(), e);
            ref.set(ref.get().failed(Instant.now(), e.getMessage()));
        }

        scheduleEviction(job.id());
    }

    private void scheduleEviction(final UUID jobId) {
        evictionExecutor.schedule(() ->
        {
            jobs.remove(jobId);
            futures.remove(jobId);
        }, TERMINAL_JOB_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void evictTerminalJobs() {
        Instant cutoff = Instant.now().minusSeconds(TERMINAL_JOB_TTL_SECONDS);
        jobs.entrySet().removeIf(entry ->
        {
            PluginInstallJob job = entry.getValue().get();
            return job.isTerminal() && job.finishedAt() != null && job.finishedAt().isBefore(cutoff);
        });
        futures.keySet().retainAll(jobs.keySet());
    }

    @PreDestroy
    public void shutdown() {
        installExecutor.shutdownNow();
        evictionExecutor.shutdownNow();
    }
}
