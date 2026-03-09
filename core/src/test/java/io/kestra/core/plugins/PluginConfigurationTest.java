package io.kestra.core.plugins;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class PluginConfigurationTest {

    @Inject
    private List<PluginConfiguration> configurations;

    @Test
    void testInjectEachProperty() {
        assertThat(this.configurations).contains(new PluginConfiguration(0, "io.kestra.plugin.Test0", Map.of("prop0", "value0")));
        assertThat(this.configurations).contains(new PluginConfiguration(1, "io.kestra.plugin.Test1", Map.of("prop1", "value1")));
        assertThat(this.configurations).contains(new PluginConfiguration(2, "io.kestra.plugin.Test2", Map.of("prop2", "value2")));
    }

}