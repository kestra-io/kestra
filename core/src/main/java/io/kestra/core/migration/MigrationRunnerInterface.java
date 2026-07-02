package io.kestra.core.migration;

/**
 * Common interface for migration runners, shared by the JDBC and Elasticsearch implementations.
 *
 * <p>
 * Implementations are Micronaut {@code @Context} beans that run eagerly on
 * {@code ApplicationContext.start()} via {@code @PostConstruct}, before any repository or
 * service bean is initialized.
 */
public interface MigrationRunnerInterface {

    /**
     * Unconditionally runs all pending migration scripts, bypassing any auto-run configuration.
     * Used by the {@code kestra migrate run} CLI command to explicitly apply pending migrations.
     *
     * @throws Exception if a migration fails
     */
    void runAlways() throws Exception;

    /**
     * Re-synchronizes the recorded checksum for an already-applied migration script.
     * Used by {@code kestra migrate repair} after an intentional migration resource update.
     *
     * @param scriptId the migration script ID to repair
     * @throws MigrationLockedException if the migration lock is held by another process
     * @throws Exception if the repair fails
     */
    default void repairChecksum(final String scriptId) throws MigrationLockedException, Exception {
        throw new UnsupportedOperationException("Migration checksum repair is not supported by " + getClass().getName());
    }

    /**
     * Runs all pending migrations, but fails immediately if the lock is held by another process.
     * Used by {@code kestra migrate run} CLI (always single-node, should not wait).
     * Server commands use {@link #runAlways()} which waits for the lock.
     *
     * @throws MigrationLockedException if the lock is held by another process
     * @throws Exception if a migration fails
     */
    void runOrFailIfLocked() throws MigrationLockedException, Exception;
}
