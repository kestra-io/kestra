package io.kestra.core.repositories.log;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.KestraRuntimeException;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link LogDataStoreInterfaceFactory}: plugin discovery and selection by
 * {@code kestra.logs.type}.
 * <p>
 * Lives in the {@code jdbc-h2} module so at least one log store ({@code H2LogDataStore}) is on the
 * classpath and discoverable via the plugin registry. It exercises the factory, not the store —
 * the store's behavior is covered by {@code H2LogDataStoreTest} (the shared log data store suite).
 */
@MicronautTest
class LogDataStoreInterfaceFactoryTest {

    @Inject
    LogDataStoreInterfaceFactory logRepositoryInterfaceFactory;

    @Test
    void shouldListDiscoveredLogDataStoreTypes() {
        assertThat(logRepositoryInterfaceFactory.getLoggableTypeIds()).contains("h2");
    }

    @Test
    void shouldFailWithClearMessageForUnknownType() {
        assertThatThrownBy(() -> logRepositoryInterfaceFactory.make("unknown", Map.of()))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("kestra.logs.type=unknown")
            .hasMessageContaining("Supported types are")
            .hasMessageContaining("h2");
    }
}
