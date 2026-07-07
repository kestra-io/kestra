package io.kestra.core.plugins;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

        installExecutor.submit(() -> runInstall(ref));

        log.info("Queued async plugin install job {} for artifacts: {}", job.id(), artifacts);
        return job.id();
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
        evictionExecutor.schedule(() -> jobs.remove(jobId), TERMINAL_JOB_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void evictTerminalJobs() {
        Instant cutoff = Instant.now().minusSeconds(TERMINAL_JOB_TTL_SECONDS);
        jobs.entrySet().removeIf(entry ->
        {
            PluginInstallJob job = entry.getValue().get();
            return job.isTerminal() && job.finishedAt() != null && job.finishedAt().isBefore(cutoff);
        });
    }

    @PreDestroy
    public void shutdown() {
        installExecutor.shutdownNow();
        evictionExecutor.shutdownNow();
    }
}
