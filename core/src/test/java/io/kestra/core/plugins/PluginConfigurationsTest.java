package io.kestra.core.plugins;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PluginConfigurationsTest {

    private static final String PLUGIN_TEST = "io.kestra.plugin.Test";

    @Test
    void shouldGetOrderedMergeConfigurationProperties() {
        // Given
        PluginConfigurations configurations = new PluginConfigurations(List.of(
            new PluginConfiguration(0, PLUGIN_TEST, Map.of(
                "prop1", "v1",
                "prop2", "v1",
                "prop3", "v1"
            )),
            new PluginConfiguration(2, PLUGIN_TEST, Map.of(
                "prop1", "v1",
                "prop2", "v2",
                "prop3", "v3"
            )),
            new PluginConfiguration(1, PLUGIN_TEST, Map.of(
                "prop1", "v2",
                "prop2", "v2",
                "prop3", "v2"
            ))
        ));
        // When
        Map<String, Object> result = configurations.getConfigurationByPluginType(PLUGIN_TEST);

        // Then
        Assertions.assertEquals(Map.of(
            "prop1", "v1",
            "prop2", "v2",
            "prop3", "v3"
        ), result);
    }
}