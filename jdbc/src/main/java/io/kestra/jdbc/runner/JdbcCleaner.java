package io.kestra.jdbc.runner;

import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
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
import java.util.List;

@Singleton
@JdbcRunnerEnabled
@Slf4j
@Requires(property = "kestra.jdbc.cleaner")
public class JdbcCleaner {
    private final JooqDSLContextWrapper dslContextWrapper;
    private final Configuration configuration;
    private final JdbcCleanerService jdbcCleanerService;
    private final Table<Record> queueTable;

    @Inject
    public JdbcCleaner(@Named("queues") JdbcTableConfig jdbcTableConfig,
                       JooqDSLContextWrapper dslContextWrapper,
                       Configuration configuration,
                       JdbcCleanerService jdbcCleanerService
    ) {
        this.dslContextWrapper = dslContextWrapper;
        this.configuration = configuration;
        this.jdbcCleanerService = jdbcCleanerService;

        this.queueTable = DSL.table(jdbcTableConfig.table());
    }

    public void deleteQueue() {
        // first, delete types that are configured more specifically
        ListUtils.emptyOnNull(configuration.getTypes()).forEach(type -> {
            dslContextWrapper.transaction(configuration -> {
                int deleted = DSL
                    .using(configuration)
                    .delete(this.queueTable)
                    .where(AbstractJdbcRepository.field("updated").lessOrEqual(ZonedDateTime.now().minus(type.getRetention()).toOffsetDateTime()))
                    .and(jdbcCleanerService.buildTypeCondition(type.getType()))
                    .execute();
                log.info("Cleaned {} records from {} for type {}", deleted, this.queueTable.getName(), type.getType());
            });
        });

        // then, delete all other records
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
        List<TypeConfiguration> types;

        @Getter
        @EachProperty(value = "types", list = true)
        public static class TypeConfiguration {
            String type;
            Duration retention;
        }
    }
}
