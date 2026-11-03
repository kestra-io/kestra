package io.kestra.core.queues.factory;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.KestraRuntimeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueuePluginInterfaceFactoryTest {

    @Test
    void shouldResolveDatabaseTypesToJdbcPluginIgnoringCase() {
        assertThat(QueuePluginInterfaceFactory.pluginId("postgres")).isEqualTo(QueuePluginInterfaceFactory.JDBC_PLUGIN_ID);
        assertThat(QueuePluginInterfaceFactory.pluginId("Postgres")).isEqualTo(QueuePluginInterfaceFactory.JDBC_PLUGIN_ID);
        assertThat(QueuePluginInterfaceFactory.pluginId("H2")).isEqualTo(QueuePluginInterfaceFactory.JDBC_PLUGIN_ID);
    }

    @Test
    void shouldLowercasePluginIdOfExternalBackend() {
        assertThat(QueuePluginInterfaceFactory.pluginId("Kafka")).isEqualTo("kafka");
    }

    @Test
    void shouldRejectVersionedQueueType() {
        assertThatThrownBy(() -> QueuePluginInterfaceFactory.pluginId("kafka:1.0.0"))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("must not carry a version");
    }
}
