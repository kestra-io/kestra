package io.kestra.core.plugins;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalPluginsRegistrarTest {

    private static final String PLUGIN_TEMPLATE_TEST = "plugin-template-test-0.24.0-SNAPSHOT.jar";

    @Test
    void shouldRegisterExternalPluginsWhenRegistryBeanIsCreated(@TempDir Path pluginsPath) throws IOException, URISyntaxException {
        Files.copy(
            Paths.get(Objects.requireNonNull(getClass().getClassLoader().getResource("plugins/" + PLUGIN_TEMPLATE_TEST)).toURI()),
            pluginsPath.resolve(PLUGIN_TEMPLATE_TEST)
        );

        try (ApplicationContext ctx = ApplicationContext.run(Map.of(ExternalPluginsRegistrar.PLUGINS_PATH_PROPERTY, pluginsPath.toString()))) {
            PluginRegistry registry = ctx.getBean(PluginRegistry.class);

            assertThat(registry.externalPlugins())
                .anyMatch(plugin -> plugin.getExternalPlugin().getLocation().getPath().startsWith(pluginsPath.toString()));
            assertThat(registry.findClassByIdentifier("io.kestra.plugin.templates.ExampleTask")).isNotNull();
        }
    }
}
