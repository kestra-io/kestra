package io.kestra.core.migration;

/**
 * Provides distributed locking for the migration process to prevent concurrent migrations
 * on multi-node deployments.
 *
 * <p>Implementations are backend-specific:
 * <ul>
 *   <li>PostgreSQL: {@code pg_advisory_lock} / {@code pg_advisory_unlock}</li>
 *   <li>MySQL: {@code GET_LOCK} / {@code RELEASE_LOCK}</li>
 *   <li>H2: no-op (H2 is embedded, single-process)</li>
 *   <li>Elasticsearch: atomic document creation with retry</li>
 * </ul>
 *
 * <p>The lock must match the repository backend, as it protects the
 * {@link MigrationHistoryStore} which lives in that backend.
 */
public interface MigrationLock {

    /**
     * Acquires the migration lock, blocking until the lock is available or the configured
     * timeout ({@code kestra.migration.lock-acquire-timeout}, default 1 hour) is exceeded.
     *
     * @throws Exception if the lock cannot be acquired
     */
    void acquire() throws Exception;

    /**
     * Releases the migration lock.
     * Must be called in a {@code finally} block to ensure the lock is always released.
     *
     * @throws Exception if the lock cannot be released
     */
    void release() throws Exception;
}
