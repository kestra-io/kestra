package io.kestra.jdbc.runner;

import io.kestra.core.queues.QueueException;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.ZonedDateTime;

@Singleton
@JdbcRunnerEnabled
@Slf4j
@Requires(property = "kestra.jdbc.cleaner")
public class JdbcCleaner {
    private final JooqDSLContextWrapper dslContextWrapper;
    private final Configuration configuration;
    protected final Table<Record> queueTable;

    @Inject
    public JdbcCleaner(@Named("queues") JdbcTableConfig jdbcTableConfig,
                       JooqDSLContextWrapper dslContextWrapper,
                       Configuration configuration
    ) {
        this.dslContextWrapper = dslContextWrapper;
        this.configuration = configuration;

        this.queueTable = DSL.table(jdbcTableConfig.table());
    }

    public void deleteQueue() throws QueueException {
        dslContextWrapper.transaction(configuration -> {
            int deleted = DSL
                .using(configuration)
                .delete(this.queueTable)
                .where(
                    AbstractJdbcRepository.field("updated")
                        .lessOrEqual(ZonedDateTime.now().minus(this.configuration.getRetention()).toOffsetDateTime())
                )
                .execute();
            log.info("Cleaned {} records from {}", deleted, this.queueTable.getName());
        });
    }

    @Scheduled(initialDelay = "${kestra.jdbc.cleaner.initial-delay}", fixedDelay = "${kestra.jdbc.cleaner.fixed-delay}")
    public void report() {
        deleteQueue();
    }

    @ConfigurationProperties("kestra.jdbc.cleaner")
    @Getter
    public static class Configuration {
        Duration retention;
    }
}
