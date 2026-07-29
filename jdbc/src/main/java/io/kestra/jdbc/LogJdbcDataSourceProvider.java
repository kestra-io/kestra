package io.kestra.jdbc;

import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.repositories.log.LogsConfig;

import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

/**
 * Resolves the JDBC datasource a log store (and its migration) should use, from {@code kestra.logs.*}.
 * <p>
 * A single resolution shared by the log store and the log-table migration so a dedicated log
 * database is described in one place (rather than a separate {@code datasources.logs} block):
 * <ul>
 * <li>if {@code kestra.logs.<type>.url} is set → build (and own) a dedicated HikariCP
 * {@link DataSource} + {@link JooqDSLContextWrapper} for it (e.g. Postgres main + MySQL logs);</li>
 * <li>otherwise → fall back to the primary {@link DataSource} (logs in the main database).</li>
 * </ul>
 * The dedicated pool is built lazily and closed on shutdown.
 */
@Singleton
public class LogJdbcDataSourceProvider implements AutoCloseable {

    private final LogsConfig logsConfig;
    private final Settings jooqSettings;
    private final DataSource primaryDataSource;
    private final RepositoryConfiguration repositoryConfiguration;

    private boolean initialized;
    private HikariDataSource dedicatedDataSource;
    private JooqDSLContextWrapper dedicatedWrapper;
    private String table = "logs";
    private SQLDialect dialect;

    public LogJdbcDataSourceProvider(final LogsConfig logsConfig,
        final Settings jooqSettings,
        @Nullable final DataSource primaryDataSource,
        final RepositoryConfiguration repositoryConfiguration) {
        this.logsConfig = logsConfig;
        this.jooqSettings = jooqSettings;
        this.primaryDataSource = primaryDataSource;
        this.repositoryConfiguration = repositoryConfiguration;
    }

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }

        Optional<String> type = logsConfig.type();
        if (type.isPresent()) {
            SQLDialect resolvedDialect = toDialect(type.get());
            if (resolvedDialect != null) {
                this.dialect = resolvedDialect;
                Map<String, Object> config = logsConfig.getLogConfig(type.get());
                Object configuredTable = config.get("table");
                if (configuredTable != null) {
                    this.table = configuredTable.toString();
                }
                Object url = config.get("url");
                if (url != null) {
                    HikariConfig hikariConfig = new HikariConfig();
                    hikariConfig.setJdbcUrl(url.toString());
                    Optional.ofNullable(config.get("username")).ifPresent(u -> hikariConfig.setUsername(u.toString()));
                    Optional.ofNullable(config.get("password")).ifPresent(p -> hikariConfig.setPassword(p.toString()));
                    hikariConfig.setPoolName("kestra-logs-" + type.get());
                    this.dedicatedDataSource = new HikariDataSource(hikariConfig);
                    this.dedicatedWrapper = new JooqDSLContextWrapper(
                        DSL.using(this.dedicatedDataSource, resolvedDialect, jooqSettings),
                        this.dedicatedDataSource
                    );
                } else {
                    // No dedicated url: the store will reuse the primary datasource, so the declared
                    // log dialect must match the main repository's dialect — otherwise we'd run the
                    // wrong dialect's SQL (or have no JDBC datasource at all when the main repo isn't JDBC).
                    SQLDialect primaryDialect = toDialect(repositoryConfiguration.type());
                    if (primaryDialect != resolvedDialect) {
                        throw new KestraRuntimeException(
                            ("Invalid log store configuration: 'kestra.logs.type=%s' does not match the main "
                                + "repository ('kestra.repository.type=%s') and no 'kestra.logs.%s.url' is configured. "
                                + "Configure a dedicated log database URL, or set 'kestra.logs.type' to match the "
                                + "repository type.").formatted(type.get(), repositoryConfiguration.type(), type.get())
                        );
                    }
                }
            }
        }

        initialized = true;
    }

    /**
     * @return {@code true} when logs are stored in a dedicated database (a {@code url} is configured).
     */
    public boolean isDedicated() {
        ensureInitialized();
        return dedicatedDataSource != null;
    }

    /**
     * @return the {@link JooqDSLContextWrapper} for the dedicated database, or {@code null} when logs
     *         use the primary datasource.
     */
    public JooqDSLContextWrapper dedicatedWrapper() {
        ensureInitialized();
        return dedicatedWrapper;
    }

    /**
     * @return the datasource to run the log-table migration against: the dedicated one when configured,
     *         otherwise the primary datasource.
     */
    public DataSource dataSource() {
        ensureInitialized();
        return dedicatedDataSource != null ? dedicatedDataSource : primaryDataSource;
    }

    /**
     * @return the configured log table name (defaults to {@code logs}).
     */
    public String table() {
        ensureInitialized();
        return table;
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
