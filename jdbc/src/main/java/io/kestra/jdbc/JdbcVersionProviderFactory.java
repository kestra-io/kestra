package io.kestra.jdbc;

import io.kestra.core.services.BackendVersionProvider;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jooq.impl.DSL;

/**
 * Factory that produces {@link BackendVersionProvider} beans for JDBC-based backends.
 * <p>
 * The database version is resolved lazily on first call to {@link BackendVersionProvider#getVersion()}
 * using {@link JooqDSLContextWrapper#transactionResult} which provides a proper connection context.
 */
@Factory
@Slf4j
public class JdbcVersionProviderFactory {

    @Singleton
    @Named("jdbcRepositoryVersionProvider")
    @Requires(property = "kestra.repository.type", pattern = "mysql|postgres|h2|memory")
    public BackendVersionProvider jdbcRepositoryVersionProvider(
        JooqDSLContextWrapper dslContextWrapper,
        Environment environment
    ) {
        String type = environment.getProperty("kestra.repository.type", String.class).orElse("jdbc");
        return new JdbcVersionProvider(dslContextWrapper, type, BackendVersionProvider.Category.REPOSITORY);
    }

    @Singleton
    @Named("jdbcQueueVersionProvider")
    @Requires(property = "kestra.queue.type", pattern = "mysql|postgres|h2|memory")
    public BackendVersionProvider jdbcQueueVersionProvider(
        JooqDSLContextWrapper dslContextWrapper,
        Environment environment
    ) {
        String type = environment.getProperty("kestra.queue.type", String.class).orElse("jdbc");
        return new JdbcVersionProvider(dslContextWrapper, type, BackendVersionProvider.Category.QUEUE);
    }

    /**
     * {@link BackendVersionProvider} that resolves the database version lazily
     * from {@link java.sql.DatabaseMetaData} within a jOOQ transaction context.
     */
    @Slf4j
    static class JdbcVersionProvider implements BackendVersionProvider {

        private final JooqDSLContextWrapper dslContextWrapper;
        private final String type;
        private final Category category;
        private volatile String cachedVersion;

        JdbcVersionProvider(JooqDSLContextWrapper dslContextWrapper, String type, Category category) {
            this.dslContextWrapper = dslContextWrapper;
            this.type = type;
            this.category = category;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Category category() {
            return category;
        }

        @Override
        public String getVersion() {
            if (cachedVersion != null) {
                return cachedVersion;
            }

            try {
                cachedVersion = dslContextWrapper.transactionResult(configuration ->
                    DSL.using(configuration).connectionResult(conn -> {
                        var meta = conn.getMetaData();
                        return meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
                    })
                );
            } catch (Exception e) {
                log.debug("Failed to retrieve database version", e);
                return null;
            }

            return cachedVersion;
        }
    }
}
