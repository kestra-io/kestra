package io.kestra.core.migration;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the execution of all pending {@link MigrationScript}s on startup.
 *
 * <p>This bean is backend-agnostic — it delegates all storage and locking concerns to
 * {@link MigrationHistoryStore} and {@link MigrationLock}, which are provided by the
 * active repository backend (JDBC or Elasticsearch).
 *
 * <p>This service is {@code @Context} — it is eagerly initialized during
 * {@code ApplicationContext.start()}, before any repository or service bean queries the database.
 * {@link #initOnStartup()} runs all pending migrations unconditionally in OSS; EE overrides
 * this method to add opt-in auto-run behavior and fresh-instance detection.
 *
 * <p>Execution flow:
 * <ol>
 *   <li>Acquire the {@link MigrationLock} (distributed lock for multi-node safety)</li>
 *   <li>Bootstrap the history store if absent</li>
 *   <li>Detect whether this is a Flyway upgrade (pre-migration-system deployment)</li>
 *   <li>Sort all {@link MigrationScript} beans lexicographically by {@code scriptId}</li>
 *   <li>For each script: if Flyway upgrade and the script is an init script ({@link #INIT_SCRIPT_IDS}),
 *       record it as applied without executing (schema already exists from Flyway, {@code executionMs=0});
 *       otherwise skip if already applied (verify checksum) or execute and record</li>
 *   <li>Release the lock</li>
 * </ol>
 */
@Slf4j
@Context
@Order(Ordered.HIGHEST_PRECEDENCE)
@Requires(property = "kestra.repository.type")
public class MigrationRunner implements MigrationRunnerInterface {

    /**
     * Script IDs that represent the initial schema creation for all backends.
     * These are skipped on upgrades from pre-migration-system deployments
     * (the schema already exists from Flyway migrations).
     */
    static final List<String> INIT_SCRIPT_IDS = List.of("0-init", "0-init-ee", "0-init-queue", "0-init-queue-ee");

    /**
     * Set to {@code true} by CLI commands (e.g. {@code migrate run}, {@code migrate unlock})
     * that handle migrations explicitly. When set, {@link #initOnStartup()} skips automatic
     * migration execution during context startup.
     */
    protected static volatile boolean skipAutoRun = false;

    public static void setSkipAutoRun(boolean skip) {
        skipAutoRun = skip;
    }

    protected volatile boolean hasRun = false;

    private final MigrationLock lock;
    protected final MigrationHistoryStore historyStore;
    private final Collection<MigrationScript> scripts;

    @Inject
    public MigrationRunner(
        final MigrationLock lock,
        final MigrationHistoryStore historyStore,
        final Collection<MigrationScript> scripts
    ) {
        this.lock = lock;
        this.historyStore = historyStore;
        this.scripts = scripts;
    }

    /**
     * Called once at context startup (eagerly, before any repository bean is initialized).
     * Delegates to {@link #autoRun()} so EE can override startup behavior without
     * needing to redeclare {@code @PostConstruct} (which is not inherited by overriding methods
     * in Micronaut's compile-time annotation processing).
     */
    @PostConstruct
    @SneakyThrows
    protected final void initOnStartup() {
        if (skipAutoRun) {
            log.debug("Migration auto-run skipped (skipAutoRun flag set).");
            return;
        }
        autoRun();
    }

    /**
     * Runs migrations at context startup. OSS always applies all pending scripts.
     * EE overrides this to respect the {@code kestra.migration.auto} configuration
     * and handle fresh-instance detection.
     *
     * @throws Exception if a migration fails
     */
    protected void autoRun() throws Exception {
        runAlways();
    }

    /**
     * Unconditionally runs all pending migration scripts, regardless of any auto-run
     * configuration. Use this method from the {@code kestra migrate run} CLI command to bypass
     * the EE startup-fail behavior.
     *
     * @throws Exception if a migration fails
     */
    @Override
    public void runAlways() throws Exception {
        if (scripts.isEmpty()) {
            log.debug("No migration scripts found, skipping migration.");
            return;
        }

        lock.acquire();
        try {
            executeMigrations();
        } finally {
            lock.release();
        }
        hasRun = true;
    }

    @Override
    public void runOrFailIfLocked() throws MigrationLockedException, Exception {
        if (hasRun) {
            return;
        }
        if (scripts.isEmpty()) {
            log.debug("No migration scripts found, skipping migration.");
            return;
        }
        if (!lock.tryAcquire()) {
            throw new MigrationLockedException();
        }
        try {
            executeMigrations();
        } finally {
            lock.release();
        }
        hasRun = true;
    }

    /**
     * Returns all scripts that have not yet been applied, sorted by {@code scriptId}.
     * Used by EE to detect pending scripts before deciding whether to run or fail.
     *
     * <p>This method does <strong>not</strong> acquire the migration lock. It is intended as a
     * read-only startup check on a single node (EE {@code auto=false} path) to decide whether to
     * throw {@link MigrationPendingException}. In multi-node deployments, callers should be aware
     * that another node may concurrently apply migrations between this call and any subsequent action.
     *
     * @return list of pending scripts
     */
    public List<MigrationScript> pendingScripts() throws Exception {
        historyStore.bootstrapIfNeeded();
        boolean isLegacyUpgrade = historyStore.detectLegacyUpgrade();

        List<MigrationScript> pending = new ArrayList<>();
        for (MigrationScript script : scripts.stream()
            .sorted(Comparator.comparing(MigrationScript::scriptId))
            .toList()) {
            if (isLegacyUpgrade && INIT_SCRIPT_IDS.contains(script.scriptId())) {
                continue;
            }
            if (!historyStore.isApplied(script.scriptId())) {
                pending.add(script);
            }
        }
        return pending;
    }

    // --- Private helpers ---

    private void executeMigrations() throws Exception {
        historyStore.bootstrapIfNeeded();

        boolean isLegacyUpgrade = historyStore.detectLegacyUpgrade();
        if (isLegacyUpgrade) {
            log.info("Detected existing Flyway-managed schema. Init scripts will be marked as applied without execution.");
        }

        List<MigrationScript> sortedScripts = scripts.stream()
            .sorted(Comparator.comparing(MigrationScript::scriptId))
            .toList();

        for (MigrationScript script : sortedScripts) {
            runScript(script, isLegacyUpgrade);
        }
    }

    private void runScript(final MigrationScript script, final boolean isLegacyUpgrade) throws Exception {
        String scriptId = script.scriptId();

        // Init scripts are skipped for pre-migration-system upgrades (schema already exists from Flyway).
        // We still record them as applied (executionMs=0) so subsequent startups don't see them as pending.
        if (isLegacyUpgrade && INIT_SCRIPT_IDS.contains(scriptId)) {
            historyStore.markApplied(script, 0L);
            log.info("Migration [{}] recorded as applied without execution (Flyway upgrade: schema pre-existing)", scriptId);
            return;
        }

        if (historyStore.isApplied(scriptId)) {
            historyStore.validateChecksum(script);
            if (!shouldForceRerun(scriptId)) {
                log.debug("Migration [{}] already applied, skipping", scriptId);
                return;
            }
            // Force-rerun: history entry already exists, skip markApplied() to avoid duplicate PK
            log.info("Force-rerunning migration [{}]: {}", scriptId, script.description());
            long start = System.currentTimeMillis();
            try {
                script.migrate();
                log.info("Migration [{}] force-rerun completed in {}ms", scriptId, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("Migration [{}] force-rerun failed", scriptId, e);
                throw e;
            }
            return;
        }

        log.info("Applying migration [{}]: {}", scriptId, script.description());
        long start = System.currentTimeMillis();
        try {
            script.migrate();
            long elapsed = System.currentTimeMillis() - start;
            historyStore.markApplied(script, elapsed);
            log.info("Migration [{}] applied successfully in {}ms", scriptId, elapsed);
        } catch (Exception e) {
            log.error("Migration [{}] failed", scriptId, e);
            throw e;
        }
    }

    /**
     * Returns {@code true} if the given script should be re-executed even when the migration
     * history already records it as applied. Defaults to {@code false} — override in subclasses
     * to implement config-driven force-rerun behaviour.
     *
     * <p>When this returns {@code true}, the checksum is still validated before execution, but
     * {@code markApplied()} is <strong>not</strong> called (history entry already exists).
     *
     * @param scriptId the script ID to check
     * @return {@code true} to force re-execution, {@code false} to skip as normal
     */
    protected boolean shouldForceRerun(final String scriptId) {
        return false;
    }
}
