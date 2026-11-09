package io.kestra.core.plugins.configuration;

import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginsConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S", "P25D"})
    void shouldRejectReadTimeoutWhenNotPositiveOrAboveIntegerMilliseconds(String readTimeout) {
        assertThatThrownBy(() -> new PluginsConfiguration(null, Duration.parse(readTimeout)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kestra.plugins.read-timeout");
    }
}
