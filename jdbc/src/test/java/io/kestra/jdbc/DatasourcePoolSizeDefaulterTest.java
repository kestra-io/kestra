package io.kestra.jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanCreatedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatasourcePoolSizeDefaulterTest {

    @Mock
    private Environment environment;

    @Mock
    private BeanCreatedEvent<DatasourceConfiguration> event;

    @Test
    void shouldRaiseDefaultPoolSizeWhenNotExplicitlyConfigured() {
        // Given
        DatasourceConfiguration configuration = new DatasourceConfiguration("postgres");
        when(event.getBean()).thenReturn(configuration);
        when(environment.containsProperty("datasources.postgres.maximum-pool-size")).thenReturn(false);

        // When
        DatasourceConfiguration result = new DatasourcePoolSizeDefaulter(environment).onCreated(event);

        // Then
        assertThat(result.getMaximumPoolSize()).isEqualTo(25);
    }

    @Test
    void shouldNotOverrideExplicitlyConfiguredPoolSize() {
        // Given
        DatasourceConfiguration configuration = new DatasourceConfiguration("postgres");
        configuration.setMaximumPoolSize(10);
        when(event.getBean()).thenReturn(configuration);
        when(environment.containsProperty("datasources.postgres.maximum-pool-size")).thenReturn(true);

        // When
        DatasourceConfiguration result = new DatasourcePoolSizeDefaulter(environment).onCreated(event);

        // Then
        assertThat(result.getMaximumPoolSize()).isEqualTo(10);
    }

    @Test
    void shouldDeriveThePropertyPathFromTheDatasourceName() {
        // Given
        DatasourceConfiguration configuration = new DatasourceConfiguration("mysql");
        when(event.getBean()).thenReturn(configuration);

        // When
        new DatasourcePoolSizeDefaulter(environment).onCreated(event);

        // Then
        verify(environment).containsProperty("datasources.mysql.maximum-pool-size");
    }
}
