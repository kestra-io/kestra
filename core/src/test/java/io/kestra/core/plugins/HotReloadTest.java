package io.kestra.core.plugins;

import io.kestra.core.utils.Await;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class HotReloadTest {
    @Test
    void addThenRemovePlugin() throws IOException, TimeoutException, URISyntaxException {
        Path hotReloadPluginsPath = Files.createTempDirectory("hot-reload-plugins");

        PluginScanner pluginScanner = new PluginScanner(ClassLoader.getSystemClassLoader());
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginScanner.continuousScan(hotReloadPluginsPath, plugin -> {
            if (plugin.getManifest() != null) {
                pluginRegistry.addPlugin(plugin);
            }
        }, pluginRegistry::removePlugin);

        String pluginName = "plugin-template-test-0.6.0-SNAPSHOT.jar";
        Path existingPluginsFolder = Paths.get(Objects.requireNonNull(HotReloadTest.class.getClassLoader().getResource("plugins")).toURI());
        Path pluginPathInHotReloadFolder = hotReloadPluginsPath.resolve(pluginName);

        Files.copy(existingPluginsFolder.resolve(pluginName), pluginPathInHotReloadFolder);

        try {
            Await.until(() -> !pluginRegistry.getPlugins().isEmpty(), Duration.ofMillis(500), Duration.ofSeconds(10));
            assertThat(pluginRegistry.getPlugins().size(), is(1));
        } finally {
            FileUtils.deleteDirectory(hotReloadPluginsPath.toFile());
        }

        Await.until(() -> pluginRegistry.getPlugins().isEmpty(), Duration.ofMillis(500), Duration.ofSeconds(10));
        assertThat(pluginRegistry.getPlugins(), empty());
    }
}
