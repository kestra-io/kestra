package io.kestra.core.plugins;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@MicronautTest
class PluginConfigurationTest {

    private static final String PLUGIN_TEST = "io.kestra.plugin.Test";

    @Inject
    private List<PluginConfiguration> configurations;

    @Test
    void testInjectEachProperty() {
        // Given
        this.configurations.sort(PluginConfiguration.COMPARATOR);

        // Then
        List<PluginConfiguration> expected = IntStream.range(0, 3)
            .mapToObj(idx -> new PluginConfiguration(idx, PLUGIN_TEST + idx, Map.of("prop" + idx, "value" + idx)))
            .toList();
        Assertions.assertEquals(expected, configurations);
    }

}