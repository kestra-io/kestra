package io.kestra.core.migration;

/**
 * Stores and queries the migration history, tracking which {@link MigrationScript}s have
 * been applied and their checksums.
 *
 * <p>Implementations are backend-specific:
 * <ul>
 *   <li>JDBC: {@code kestra_migration_history} SQL table</li>
 *   <li>Elasticsearch: {@code .kestra-migration-history} index</li>
 * </ul>
 *
 * <p>The history store must live in the repository backend so that the migration state
 * is co-located with the data it governs. This also guarantees a single ordered history
 * when multiple script types (SQL, ELS, Java) run in the same migration sequence.
 */
public interface MigrationHistoryStore {

    /**
     * Creates the history storage (table or index) if it does not already exist.
     *
     * @throws Exception if bootstrap fails
     */
    void bootstrapIfNeeded() throws Exception;

    /**
     * Returns whether any migration script has been successfully applied.
     * Used to distinguish a fresh install from an upgrade.
     *
     * @return {@code true} if at least one script has been applied
     * @throws Exception on backend error
     */
    boolean hasAnyApplied() throws Exception;

    /**
     * Returns whether the given script has been successfully applied.
     *
     * @param scriptId the script identifier
     * @return {@code true} if the script was applied successfully
     * @throws Exception on backend error
     */
    boolean isApplied(String scriptId) throws Exception;

    /**
     * Validates that the stored checksum for an already-applied script matches the
     * current script's checksum. Throws if they differ — migration scripts must not
     * be modified after they have been applied.
     *
     * <p>Implementations must skip validation when {@link MigrationScript#checksum()}
     * returns {@code null} (Java-only migrations with no stable checksum source).
     *
     * @param script the script whose checksum should be validated
     * @throws IllegalStateException if the checksum does not match
     * @throws Exception             on backend error
     */
    void validateChecksum(MigrationScript script) throws Exception;

    /**
     * Records a successfully applied migration script in the history.
     *
     * @param script      the applied script
     * @param executionMs elapsed time in milliseconds
     * @throws Exception on backend error
     */
    void markApplied(MigrationScript script, long executionMs) throws Exception;

    /**
     * Detects whether this is an upgrade from a pre-migration-system deployment.
     *
     * <p>For JDBC backends, checks for the presence of {@code flyway_schema_history}.
     * For Elasticsearch, checks whether a core index (e.g. {@code {prefix}flows}) already has documents.
     *
     * @return {@code true} if upgrading from a pre-migration-system deployment
     * @throws Exception on backend error
     */
    boolean detectLegacyUpgrade() throws Exception;
}
