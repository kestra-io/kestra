package io.kestra.core.migration;

/**
 * Represents a single versioned migration script.
 *
 * <p>Implementations are Micronaut {@code @Singleton} beans that {@code @Inject} whatever
 * dependencies they need (e.g. {@code DSLContext}, repositories, OpenSearch client).  No context
 * object is passed to {@link #migrate()}; the script is fully responsible for obtaining its own
 * resources through DI.
 *
 * <p>Scripts are collected by the active migration runner, sorted lexicographically by
 * {@link #scriptId()}, and executed in that order.
 *
 * <p>Script ID naming convention:
 * <ul>
 *   <li>Init scripts (fresh install only, skipped on Flyway upgrade):
 *       {@code "0-init"}, {@code "0-init-ee"}, {@code "0-init-queue"}, {@code "0-init-queue-ee"}</li>
 *   <li>OSS versioned scripts: {@code "2.0"}, {@code "2.1"}, …</li>
 *   <li>EE versioned scripts (JDBC and Elasticsearch): {@code "2.0-ee"}, {@code "2.1-ee"}, …</li>
 *   <li>OSS queue scripts: {@code "2.0-queue"}, {@code "2.1-queue"}, …</li>
 * </ul>
 * The {@code "0-"} prefix ensures init scripts always sort before versioned scripts.
 * Within versioned scripts, EE JDBC and EE Elasticsearch share the {@code "-ee"} suffix
 * because only one repository backend is active at a time, so there is no ambiguity.
 * Lexicographic ordering ensures OSS scripts run before EE scripts of the same version
 * ({@code "2.0" < "2.0-ee" < "2.0-queue"}).
 */
public interface MigrationScript {

    /**
     * Unique identifier for this script, used for lexicographic ordering and history tracking.
     *
     * @return the script ID, e.g. {@code "2.0"} or {@code "2.0-ee"}
     */
    String scriptId();

    /**
     * Human-readable description of what this script does.
     *
     * @return a short description
     */
    String description();

    /**
     * A stable, developer-defined constant used for integrity verification.
     * If a script is found in the history table with a different checksum, startup fails.
     *
     * @return a non-null, non-empty stable string identifying this script version
     */
    String checksum();

    /**
     * Executes the migration.
     *
     * @throws Exception if the migration fails
     */
    void migrate() throws Exception;
}
