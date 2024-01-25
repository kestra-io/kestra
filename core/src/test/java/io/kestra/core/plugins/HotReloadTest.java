package io.kestra.core.plugins;

import io.kestra.core.utils.Await;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class HotReloadTest {
    @Test
    void addThenRemovePlugin() throws IOException, TimeoutException, URISyntaxException {
        Path hotReloadPluginsPath = Files.createTempDirectory("hot-reload-plugins");

        PluginScanner pluginScanner = new PluginScanner(ClassLoader.getSystemClassLoader());
        PluginRegistry pluginRegistry = new PluginRegistry();
        pluginScanner.continuousScan(hotReloadPluginsPath, pluginRegistry);

        String pluginName = "plugin-template-test-0.6.0-SNAPSHOT.jar";
        Path existingPluginsFolder = Paths.get(Objects.requireNonNull(HotReloadTest.class.getClassLoader().getResource("plugins")).toURI());
        Path pluginPathInHotReloadFolder = hotReloadPluginsPath.resolve(pluginName);

        Files.copy(existingPluginsFolder.resolve(pluginName), pluginPathInHotReloadFolder);

        try {
            Await.until(() -> !pluginRegistry.getPlugins().isEmpty(), Duration.ofMillis(500), Duration.ofSeconds(10));
            assertThat(pluginRegistry.getPlugins().size(), is(1));
            assertThat(pluginRegistry.getPluginsByClass().size(), is(1));
            assertThat(pluginRegistry.getPluginsByClass(), hasKey("io.kestra.plugin.templates.ExampleTask"));

            // replacement
            Files.copy(Paths.get(Objects.requireNonNull(HotReloadTest.class.getClassLoader().getResource("replacement_plugins")).toURI()).resolve(pluginName), pluginPathInHotReloadFolder, StandardCopyOption.REPLACE_EXISTING);
            Await.until(() -> !pluginRegistry.getPluginsByClass().containsKey("io.kestra.plugin.templates.ExampleTask") && !pluginRegistry.getPlugins().isEmpty(), Duration.ofMillis(500), Duration.ofSeconds(10));
            assertThat(pluginRegistry.getPlugins().size(), is(1));
            assertThat(pluginRegistry.getPluginsByClass().size(), is(1));
            assertThat(pluginRegistry.getPluginsByClass(), hasKey("io.kestra.plugin.templates.AnotherTask"));
        } finally {
            FileUtils.deleteDirectory(hotReloadPluginsPath.toFile());
        }

        Await.until(() -> pluginRegistry.getPlugins().isEmpty(), Duration.ofMillis(500), Duration.ofSeconds(10));
        assertThat(pluginRegistry.getPlugins(), empty());
    }
}
