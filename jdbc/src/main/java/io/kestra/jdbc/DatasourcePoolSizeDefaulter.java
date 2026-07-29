package io.kestra.jdbc;

import java.util.Objects;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Raises HikariCP's default maximum pool size (10) to a value better suited to the JDBC queue,
 * which polls with one dedicated thread per queue type/routing key and can hold a connection for
 * the duration of message processing. Only applies when the user has not explicitly configured
 * {@code datasources.<name>.maximum-pool-size} themselves, and only for whichever datasource is
 * actually declared (it never creates a datasource for a dialect that isn't configured).
 */
@Singleton
public class DatasourcePoolSizeDefaulter implements BeanCreatedEventListener<DatasourceConfiguration> {
    private static final int DEFAULT_MAXIMUM_POOL_SIZE = 25;

    private final Environment environment;

    @Inject
    public DatasourcePoolSizeDefaulter(Environment environment) {
        this.environment = Objects.requireNonNull(environment);
    }

    @Override
    public DatasourceConfiguration onCreated(BeanCreatedEvent<DatasourceConfiguration> event) {
        DatasourceConfiguration configuration = event.getBean();

        if (!environment.containsProperty("datasources." + configuration.getName() + ".maximum-pool-size")) {
            configuration.setMaximumPoolSize(DEFAULT_MAXIMUM_POOL_SIZE);
        }

        return configuration;
    }
}
