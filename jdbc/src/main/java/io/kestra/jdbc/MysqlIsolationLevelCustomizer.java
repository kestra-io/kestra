package io.kestra.jdbc;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Ensures MySQL HikariCP connection pools use READ_COMMITTED isolation level.
 *
 * <p>MySQL defaults to REPEATABLE_READ, but Kestra's queue system requires READ_COMMITTED.
 * This listener auto-configures the HikariCP config before the pool is created, so users
 * upgrading from older versions (which had no such requirement) don't need to update
 * their datasource configuration manually.
 */
@Slf4j
@Singleton
public class MysqlIsolationLevelCustomizer implements BeanCreatedEventListener<DatasourceConfiguration> {

    private static final String READ_COMMITTED = "TRANSACTION_READ_COMMITTED";

    @Override
    public DatasourceConfiguration onCreated(BeanCreatedEvent<DatasourceConfiguration> event) {
        DatasourceConfiguration config = event.getBean();

        if (isMysql(config) && config.getTransactionIsolation() == null) {
            config.setTransactionIsolation(READ_COMMITTED);
            log.info("Auto-configured MySQL connection pool isolation level to READ_COMMITTED.");
        }

        return config;
    }

    private static boolean isMysql(DatasourceConfiguration config) {
        String url = config.getJdbcUrl();
        return url != null && url.contains("mysql");
    }
}
