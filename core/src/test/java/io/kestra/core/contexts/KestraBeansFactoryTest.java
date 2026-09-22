package io.kestra.core.contexts;

import java.util.Map;

import io.kestra.core.repositories.log.LogsConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KestraBeansFactoryTest {

    @Test
    void shouldRejectExternalLogStoreWhenLogTypeConfigured() {
        KestraBeansFactory factory = new KestraBeansFactory();
        factory.logsConfig = new LogsConfig(Map.of("type", "elasticsearch"));

        assertThatThrownBy(factory::ensureLogDataStoreAllowed)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kestra.logs.type=elasticsearch")
            .hasMessageContaining("Enterprise Edition");
    }

    @Test
    void shouldAllowLogDataStoreWhenNoLogTypeConfigured() {
        KestraBeansFactory factory = new KestraBeansFactory();
        factory.logsConfig = new LogsConfig(null);

        assertThatCode(factory::ensureLogDataStoreAllowed).doesNotThrowAnyException();
    }
}
