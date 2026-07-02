package io.kestra.core.migration;

import io.micronaut.core.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Represents a single versioned migration script.
 *
 * <p>
 * Implementations are Micronaut {@code @Singleton} beans. Because {@code MigrationRunner} is a
 * {@code @Context} bean, all dependencies injected into a script are eagerly instantiated during
 * application startup. Scripts must only depend on low-level infrastructure:
 * {@code DataSource} / {@code JooqDSLContextWrapper} for JDBC, {@code OpenSearchClient} for
 * Elasticsearch, storages, and simple {@code @ConfigurationProperties} records. Never inject
 * repositories or services — their transitive dependencies will fail to initialize at this early
 * stage. No context object is passed to {@link #migrate()}; the script is fully responsible for
 * obtaining its own resources through DI.
 *
 * <p>
 * Scripts are collected by the active migration runner, sorted lexicographically by
 * {@link #scriptId()}, and executed in that order.
 *
 * <p>
 * Script ID naming convention: {@code <major>.<minor>.<two-digit-increment>-description}.
 * The two-digit increment (01–99) defines execution order within a minor version.
 * <ul>
 * <li>Init scripts (fresh install only, skipped on Flyway upgrade; frozen, special case):
 * {@code "0-init"}, {@code "0-init-ee"}, {@code "0-init-queue"}, {@code "0-init-queue-ee"}</li>
 * <li>OSS versioned scripts: {@code "2.0.01-upgrade"}, {@code "2.0.04-mcp"}, …</li>
 * <li>EE versioned scripts (JDBC and Elasticsearch): {@code "2.0.02-upgrade-ee"}, …</li>
 * <li>OSS queue scripts: {@code "2.0.05-queue"}, …</li>
 * </ul>
 * The {@code "0-"} prefix ensures init scripts always sort before versioned scripts.
 * Within versioned scripts, the two-digit increment controls execution order.
 * Lexicographic ordering ensures scripts run in the intended sequence
 * ({@code "2.0.01-upgrade" < "2.0.02-upgrade-ee" < "2.0.05-queue"}).
 */
public interface MigrationScript {

    /**
     * Unique identifier for this script, used for lexicographic ordering and history tracking.
     *
     * @return the script ID, e.g. {@code "2.0.01-upgrade"} or {@code "2.0.02-upgrade-ee"}
     */
    String scriptId();

    /**
     * Human-readable description of what this script does.
     *
     * @return a short description
     */
    String description();

    /**
     * A stable checksum used for integrity verification.
     * If a script is found in the history table with a different checksum, startup fails.
     *
     * <p>For SQL-based migrations, use {@link #checksumOfResources(String...)} to derive the
     * checksum from the SQL file content — any change to the file is detected automatically.
     *
     * <p>For Java-only migrations (no SQL resource), return {@code null} to skip checksum
     * validation. This follows the Flyway convention: Java bytecode is not a stable hash
     * source (it varies across JDK versions), so checksum verification is not meaningful
     * for pure Java scripts.
     *
     * @return a stable checksum string for SQL migrations, or {@code null} for Java-only migrations
     */
    @Nullable
    String checksum();

    /**
     * Executes the migration.
     *
     * @throws Exception if the migration fails
     */
    void migrate() throws Exception;

    /**
     * Computes a SHA-256 checksum from the content of one or more classpath resources.
     *
     * <p>
     * Use this to derive a stable, content-based checksum for migration scripts.
     * If any resource changes, the checksum changes automatically.
     *
     * <p>
     * Usage examples:
     * 
     * <pre>{@code
     * // Pure SQL migration — single resource
     * checksumOfResources("/migrations/baseline-h2.sql")
     *
     * // SQL + Java migration — SQL resource tracked automatically;
     * // if the Java logic changes independently, add a version marker resource
     * checksumOfResources("/migrations/2.0.01-upgrade-h2.sql")
     * }</pre>
     *
     * @param resourcePaths one or more classpath resource paths to hash
     * @return a hex-encoded SHA-256 digest of the concatenated resource contents
     * @throws IllegalArgumentException if a resource is not found on the classpath
     * @throws IllegalStateException if hashing fails
     */
    static String checksumOfResources(final String... resourcePaths) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = MigrationScript.class.getClassLoader();
            }
            for (String path : resourcePaths) {
                String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
                try (InputStream is = cl.getResourceAsStream(normalizedPath)) {
                    if (is == null) {
                        throw new IllegalArgumentException("Resource not found on classpath: " + path);
                    }
                    digest.update(is.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource for checksum", e);
        }
    }
}
