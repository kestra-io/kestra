package io.kestra.runner.memory;

import java.util.Objects;

import io.kestra.jdbc.EphemeralDatabase;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;

/**
 * Repoints every datasource at the ephemeral in-memory database declared by
 * {@link EphemeralDatabase#URL_PROPERTY}.
 * <p>
 * Micronaut turns each user-chosen {@code datasources.<name>} entry into an eagerly-initialized
 * Hikari pool, and the names cannot be known by a command in advance, so a command cannot switch
 * them off through configuration. Rewriting the configuration is what makes the ephemeral run
 * possible: the pool is built from this bean by {@code DatasourceFactory}, which runs after the
 * listener, so the configured database is never connected to.
 */
@Singleton
@Order(Ordered.HIGHEST_PRECEDENCE)
@Requires(property = EphemeralDatabase.URL_PROPERTY, pattern = ".+")
public class EphemeralDatasourceRewriter implements BeanCreatedEventListener<DatasourceConfiguration> {
    private static final String H2_DRIVER = "org.h2.Driver";

    private final String url;

    public EphemeralDatasourceRewriter(@Value("${" + EphemeralDatabase.URL_PROPERTY + "}") String url) {
        this.url = Objects.requireNonNull(url);
    }

    @Override
    public DatasourceConfiguration onCreated(BeanCreatedEvent<DatasourceConfiguration> event) {
        DatasourceConfiguration configuration = event.getBean();

        configuration.setUrl(url);
        configuration.setDriverClassName(H2_DRIVER);
        configuration.setUsername("sa");
        configuration.setPassword("");

        // HikariCP picks its connection source in the order dataSource, dataSourceClassName,
        // jdbcUrl, then JNDI name, so the URL above only wins once the earlier ones are cleared.
        configuration.setDataSourceClassName(null);
        configuration.setJndiName(null);

        // Whatever remains describes the database we just stopped pointing at: driver properties
        // and an init statement are rejected outright by H2, and its schema and catalog do not
        // exist in a database created empty a moment ago.
        configuration.getDataSourceProperties().clear();
        configuration.setConnectionInitSql(null);
        configuration.setConnectionTestQuery(null);
        configuration.setCatalog(null);
        configuration.setSchema(null);

        return configuration;
    }
}
