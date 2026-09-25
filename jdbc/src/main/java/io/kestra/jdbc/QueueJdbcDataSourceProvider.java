package io.kestra.jdbc;

import java.util.Optional;

import javax.sql.DataSource;

import org.jooq.SQLDialect;
import org.jooq.conf.Settings;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.jdbc.runner.QueueJdbcConfiguration;

import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

/**
 * Resolves the JDBC datasource and {@link JooqDSLContextWrapper} the queue should use, from
 * {@code kestra.queue.jdbc.*}.
 * <p>
 * Mirrors {@link LogJdbcDataSourceProvider} but for the queue:
 * <ul>
 * <li>if {@code kestra.queue.jdbc.url} is set → build (and own) a dedicated HikariCP
 * {@link DataSource} + {@link JooqDSLContextWrapper} for it (e.g. Postgres main + MySQL queue);</li>
 * <li>otherwise → reuse the primary {@link DataSource} and {@link JooqDSLContextWrapper}
 * (queue in the main database).</li>
 * </ul>
 * The dedicated pool is built lazily and closed on shutdown.
 */
@Singleton
public class QueueJdbcDataSourceProvider implements AutoCloseable {

    private final QueueJdbcConfiguration queueJdbcConfiguration;
    private final Settings jooqSettings;
    private final DataSource primaryDataSource;
    private final JooqDSLContextWrapper primaryWrapper;
    private final RepositoryConfiguration repositoryConfiguration;

    private boolean initialized;
    private HikariDataSource dedicatedDataSource;
    private JooqDSLContextWrapper dedicatedWrapper;
    private SQLDialect dialect;

    public QueueJdbcDataSourceProvider(final QueueJdbcConfiguration queueJdbcConfiguration,
        final Settings jooqSettings,
        @Nullable final DataSource primaryDataSource,
        @Nullable final JooqDSLContextWrapper primaryWrapper,
        final RepositoryConfiguration repositoryConfiguration) {
        this.queueJdbcConfiguration = queueJdbcConfiguration;
        this.jooqSettings = jooqSettings;
        this.primaryDataSource = primaryDataSource;
        this.primaryWrapper = primaryWrapper;
        this.repositoryConfiguration = repositoryConfiguration;
    }

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }

        Optional<String> type = Optional.ofNullable(queueJdbcConfiguration.type());
        if (type.isPresent()) {
            SQLDialect resolvedDialect = toDialect(type.get());
            if (resolvedDialect != null) {
                this.dialect = resolvedDialect;
                if (queueJdbcConfiguration.url() != null) {
                    String username = queueJdbcConfiguration.username();
                    if (username == null || username.isBlank()) {
                        throw new KestraRuntimeException(
                            ("A dedicated queue database URL is configured ('kestra.queue.jdbc.url') but no username "
                                + "('kestra.queue.jdbc.username'). Configure the credentials for the dedicated queue database "
                                + "explicitly; they are never inherited from the main datasource.").formatted()
                        );
                    }
                    HikariConfig hikariConfig = new HikariConfig();
                    hikariConfig.setJdbcUrl(queueJdbcConfiguration.url());
                    hikariConfig.setUsername(username);
                    Optional.ofNullable(queueJdbcConfiguration.password()).ifPresent(p -> hikariConfig.setPassword(p));
                    hikariConfig.setPoolName("kestra-queue-" + type.get());
                    this.dedicatedDataSource = new HikariDataSource(hikariConfig);
                    this.dedicatedWrapper = new JooqDSLContextWrapper(
                        org.jooq.impl.DSL.using(this.dedicatedDataSource, resolvedDialect, jooqSettings),
                        this.dedicatedDataSource
                    );
                } else {
                    // No dedicated url: the queue will reuse the primary datasource, so the declared
                    // queue dialect must match the main repository's dialect — otherwise we'd run the
                    // wrong dialect's SQL (or have no JDBC datasource at all when the main repo isn't JDBC).
                    SQLDialect primaryDialect = toDialect(repositoryConfiguration.type());
                    if (primaryDialect != resolvedDialect) {
                        throw new KestraRuntimeException(
                            ("Invalid queue store configuration: 'kestra.queue.jdbc.type=%s' does not match the main "
                                + "repository ('kestra.repository.type=%s') and no 'kestra.queue.jdbc.url' is configured. "
                                + "Configure a dedicated queue database URL, or set 'kestra.queue.jdbc.type' to match the "
                                + "repository type.").formatted(type.get(), repositoryConfiguration.type())
                        );
                    }
                }
            }
        }

        initialized = true;
    }

    /**
     * @return {@code true} when the queue uses a dedicated database (a {@code url} is configured).
     */
    public boolean isDedicated() {
        ensureInitialized();
        return dedicatedDataSource != null;
    }

    /**
     * @return the {@link JooqDSLContextWrapper} for the dedicated database, or {@code null} when the queue
     *         uses the primary datasource.
     */
    @Nullable
    public JooqDSLContextWrapper dedicatedWrapper() {
        ensureInitialized();
        return dedicatedWrapper;
    }

    /**
     * @return the {@link JooqDSLContextWrapper} the queue should use: the dedicated one when configured,
     *         otherwise the primary datasource's wrapper. May return {@code null} when no JDBC datasource
     *         is available at all (e.g. non-JDBC repository/queue backends).
     */
    @Nullable
    public JooqDSLContextWrapper wrapper() {
        ensureInitialized();
        return dedicatedWrapper != null ? dedicatedWrapper : primaryWrapper;
    }

    /**
     * @return the datasource to run the queue-table migration against: the dedicated one when configured,
     *         otherwise the primary datasource.
     */
    @Nullable
    public DataSource dataSource() {
        ensureInitialized();
        return dedicatedDataSource != null ? dedicatedDataSource : primaryDataSource;
    }

    /**
     * @return the configured queue table name, or the default {@code "queues"} when none is configured.
     */
    public String table() {
        ensureInitialized();
        return queueJdbcConfiguration.table() != null ? queueJdbcConfiguration.table() : "queues";
    }

    @Nullable
    public SQLDialect dialect() {
        ensureInitialized();
        return dialect;
    }

    private static SQLDialect toDialect(final String type) {
        if (type == null) {
            return null;
        }
        return switch (type.toLowerCase()) {
            case "h2", "memory" -> SQLDialect.H2;
            case "postgres" -> SQLDialect.POSTGRES;
            case "mysql" -> SQLDialect.MYSQL;
            default -> null;
        };
    }

    @Override
    @PreDestroy
    public void close() {
        if (dedicatedDataSource != null) {
            dedicatedDataSource.close();
        }
    }
}
