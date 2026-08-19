package io.kestra.core.migration;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import io.micronaut.core.annotation.Nullable;

/**
 * Represents a single versioned migration script.
 *
 * <p>
 * Implementations are Micronaut {@code @Singleton} beans. Because the {@code @Context}
 * {@code MigrationStartupRunner} resolves the runner (and therefore every script) during
 * application startup, all dependencies injected into a script are eagerly instantiated at that
 * early stage. Scripts must only depend on low-level infrastructure:
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
 * <li>OSS versioned scripts: {@code "2.0.01-schema"}, {@code "2.0.02-queue"}, …</li>
 * <li>EE versioned scripts (JDBC and Elasticsearch): {@code "2.0.02-ee-schema"}, …</li>
 * </ul>
 * The {@code "0-"} prefix ensures init scripts always sort before versioned scripts.
 * Within versioned scripts, the two-digit increment controls execution order.
 * Lexicographic ordering ensures scripts run in the intended sequence
 * ({@code "2.0.01-schema" < "2.0.02-ee-schema" < "2.0.02-queue"}).
 * The OSS and EE sequences are numbered independently (each starts its own count after the
 * baseline) since their ids never collide, but a shared conceptual step spanning both — e.g. a
 * pure-Java data migration with a JDBC and an Elasticsearch implementation — reuses the exact same
 * id string across modules so the two are recognized as one migration, not two.
 *
 * <p>
 * Prefer growing an existing consolidated script over adding a new incremental one: the 2.0.x
 * scripts were themselves consolidated (see {@code AbstractV2_0_01SchemaMigration},
 * {@code AbstractV2_0_02QueueMigration}) from ~20 per-change scripts that had accumulated during the
 * 2.0 development cycle, each with its own SQL resource and Java class for what was, from a user's
 * point of view, a single "upgrade to 2.0" step. Add a new script only for a genuinely new group
 * (different {@code @Requires} condition or datasource) or once a consolidated group has shipped in a
 * GA release and must not be edited further.
 */
public interface MigrationScript {

    /**
     * Unique identifier for this script, used for lexicographic ordering and history tracking.
     *
     * @return the script ID, e.g. {@code "2.0.01-schema"} or {@code "2.0.02-ee-schema"}
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
     * <p>
     * For SQL-based migrations, use {@link #checksumOfResources(String...)} to derive the
     * checksum from the SQL file content — any change to the file is detected automatically.
     *
     * <p>
     * For Java-only migrations (no SQL resource), return {@code null} to skip checksum
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
     * Returns whether this script is applicable on this instance right now.
     *
     * <p>
     * When {@code false}, the script is skipped <strong>without</strong> being recorded in the
     * migration history, so it stays pending and runs on a later startup once applicable — unlike
     * an applied script, which never re-runs. Use this for migrations gated on configuration
     * (e.g. a feature flag) that an operator may enable after the first upgrade.
     *
     * @return {@code true} (the default) to run and record the script as usual
     */
    default boolean shouldRun() {
        return true;
    }

    /**
     * Classpath path(s) to the SQL resource(s) this migration executes, in execution order.
     *
     * <p>
     * This is the single source of truth for a SQL-backed migration's resource: SQL migrations
     * declare it here and derive their {@link #checksum()} (and, where applicable, {@link #migrate()})
     * from it. It also lets tooling preview the SQL a migration would run without applying it
     * (e.g. the {@code migrate plan --sql} command).
     *
     * @return the classpath resource path(s), or an empty list for migrations that run no SQL resource
     *         (e.g. pure-Java migrations)
     */
    default List<String> sqlResources() {
        return List.of();
    }

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
     * checksumOfResources("/migrations/2.0.01-schema-h2.sql")
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
