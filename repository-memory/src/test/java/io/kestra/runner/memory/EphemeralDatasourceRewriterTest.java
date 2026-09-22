package io.kestra.runner.memory;

import org.junit.jupiter.api.Test;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.event.BeanCreatedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EphemeralDatasourceRewriterTest {
    private static final String EPHEMERAL_URL = "jdbc:h2:mem:flow-test-1;DB_CLOSE_DELAY=-1";

    @Test
    @SuppressWarnings("unchecked")
    void shouldLeaveNothingOfTheConfiguredDatabaseBehind() {
        // Given a datasource describing a database elsewhere, through every field HikariCP would
        // accept a connection source or connection settings from.
        DatasourceConfiguration configured = new DatasourceConfiguration("postgres");
        configured.setUrl("jdbc:postgresql://db.internal:5432/kestra");
        configured.setUsername("kestra");
        configured.setPassword("k3str4");
        configured.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
        configured.setJndiName("java:comp/env/jdbc/kestra");
        configured.addDataSourceProperty("sslmode", "require");
        configured.setConnectionInitSql("SET search_path TO kestra");
        configured.setConnectionTestQuery("SELECT 1");
        configured.setCatalog("kestra");
        configured.setSchema("kestra");

        BeanCreatedEvent<DatasourceConfiguration> event = mock(BeanCreatedEvent.class);
        when(event.getBean()).thenReturn(configured);

        // When
        DatasourceConfiguration rewritten = new EphemeralDatasourceRewriter(EPHEMERAL_URL).onCreated(event);

        // Then: asserted through the fields HikariCP copies out of the configuration, rather than
        // the calculated getters, which cache a value derived before the rewrite.
        assertThat(rewritten.getJdbcUrl()).isEqualTo(EPHEMERAL_URL);
        assertThat(rewritten.getConfiguredDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(rewritten.getConfiguredUsername()).isEqualTo("sa");
        assertThat(rewritten.getConfiguredPassword()).isEmpty();
        assertThat(rewritten.getDataSourceClassName()).isNull();
        assertThat(rewritten.getDataSourceJNDI()).isNull();
        assertThat(rewritten.getDataSourceProperties()).isEmpty();
        assertThat(rewritten.getConnectionInitSql()).isNull();
        assertThat(rewritten.getConnectionTestQuery()).isNull();
        assertThat(rewritten.getCatalog()).isNull();
        assertThat(rewritten.getSchema()).isNull();
    }
}
