package io.kestra.core.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.core.PluginTemplateJar;

import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalPluginsRegistrarTest {

    @Test
    void shouldRegisterExternalPluginsWhenRegistryBeanIsCreated(@TempDir Path pluginsPath) throws IOException {
        PluginTemplateJar.copyTo(pluginsPath);

        try (ApplicationContext ctx = ApplicationContext.run(Map.of(ExternalPluginsRegistrar.PLUGINS_PATH_PROPERTY, pluginsPath.toString()))) {
            PluginRegistry registry = ctx.getBean(PluginRegistry.class);

            assertThat(registry.externalPlugins())
                .anyMatch(plugin -> plugin.getExternalPlugin().getLocation().getPath().startsWith(pluginsPath.toString()));
            assertThat(registry.findClassByIdentifier("io.kestra.plugin.templates.ExampleTask")).isNotNull();
        }
    }
}
