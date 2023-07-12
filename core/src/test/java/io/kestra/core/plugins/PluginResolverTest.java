package io.kestra.core.plugins;

import io.kestra.core.utils.Await;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PluginResolverTest {
    @Test
    void watch() throws Exception {
        // plugins already in folder
        Path existingPluginsFolder = Paths.get(Objects.requireNonNull(PluginResolverTest.class.getClassLoader().getResource("plugins")).toURI());
        List<ExternalPlugin> externalPlugins = new ArrayList<>();
        try (PluginResolver pluginResolver = new PluginResolver(existingPluginsFolder)) {
            pluginResolver.watch(externalPlugins::add);
            assertThat(externalPlugins.size(), is(1));
        }

        // hot-reload with dynamic plugins based on watched plugins folder
        Path hotReloadPluginsFolderPath = Files.createTempDirectory("hot-reload-plugins");
        externalPlugins.clear();
        try (PluginResolver pluginResolver = new PluginResolver(hotReloadPluginsFolderPath)) {
            pluginResolver.watch(externalPlugins::add);

            assertThat(externalPlugins.size(), is(0));

            String pluginName = "plugin-template-test-0.6.0-SNAPSHOT.jar";
            Files.copy(existingPluginsFolder.resolve(pluginName), hotReloadPluginsFolderPath.resolve(pluginName));

            Await.until(() -> externalPlugins.size() > 0, Duration.ofMillis(500), Duration.ofSeconds(10));
            assertThat(externalPlugins.size(), is(1));
        } finally {
            FileUtils.deleteDirectory(hotReloadPluginsFolderPath.toFile());
        }
    }
}