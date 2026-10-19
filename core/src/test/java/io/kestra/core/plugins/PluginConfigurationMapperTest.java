package io.kestra.core.plugins;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginConfigurationMapperTest {

    record Configured(Duration timeout) {
    }

    @ParameterizedTest
    @CsvSource({
        "60s, PT60S",
        "5m, PT5M",
        "10ms, PT0.01S",
        "2h, PT2H",
        "1d, PT24H",
        "-30s, PT-30S",
        "PT90S, PT90S",
        "45, PT45S",
    })
    void shouldBindDurationLiteralsWhenConfigured(String literal, Duration expected) {
        Configured configured = PluginConfigurationMapper.convert(Map.of("timeout", literal), Configured.class, true);

        assertThat(configured.timeout()).isEqualTo(expected);
    }

    @Test
    void shouldRejectUnknownDurationSuffixWhenConfigured() {
        assertThatThrownBy(() -> PluginConfigurationMapper.convert(Map.of("timeout", "5n"), Configured.class, true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUnknownPropertyWhenStrict() {
        assertThatThrownBy(() -> PluginConfigurationMapper.convert(Map.of("timeOut", "5s"), Configured.class, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeOut");
    }

    @Test
    void shouldIgnoreEnvironmentPermutationsWhenLenient() {
        Configured configured = PluginConfigurationMapper.convert(Map.of("timeout", "5s", "time", Map.of("out", "5s")), Configured.class, false);

        assertThat(configured.timeout()).isEqualTo(Duration.ofSeconds(5));
    }
}
