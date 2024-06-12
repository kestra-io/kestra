package io.kestra.core.plugins;

import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;

@KestraTest
class PluginConfigurationTest {

    @Inject
    private List<PluginConfiguration> configurations;

    @Test
    void testInjectEachProperty() {
        assertThat(this.configurations, hasItem(new PluginConfiguration(0, "io.kestra.plugin.Test0", Map.of("prop0", "value0"))));
        assertThat(this.configurations, hasItem(new PluginConfiguration(1, "io.kestra.plugin.Test1", Map.of("prop1", "value1"))));
        assertThat(this.configurations, hasItem(new PluginConfiguration(2, "io.kestra.plugin.Test2", Map.of("prop2", "value2"))));
    }

}