package io.kestra.cli.commands.servers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for https://github.com/kestra-io/kestra/issues/19736.
 *
 * <p>
 * {@code server standalone --flow-path} loaded flows before external plugins were registered,
 * so any flow using an external plugin type failed with {@code Invalid type} and was skipped.
 * Loading through the same {@link LocalFlowRepositoryLoader} code path must therefore fail while
 * the plugin is unregistered (the buggy order) and succeed once it is registered (the fixed order,
 * where {@code super.call()} initializes plugins first).
 * </p>
 */
@KestraTest
@Property(name = "kestra.plugins.auto-install.enabled", value = "false")
class StandAloneFlowPathPluginTest {
    private static final String EXTERNAL_TYPE = "io.kestra.plugin.templates.ExampleTask";
    private static final String PLUGIN_JAR = "plugins/plugin-template-test-0.24.0-SNAPSHOT.jar";

    @Inject
    private LocalFlowRepositoryLoader loader;

    @Inject
    private PluginRegistry pluginRegistry;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldLoadExternalPluginFlowOncePluginsAreRegistered(@TempDir Path temp) throws Exception {
        Path pluginsDir = Files.createDirectory(temp.resolve("plugins"));
        FileUtils.copyFile(
            new File(Objects.requireNonNull(getClass().getClassLoader().getResource(PLUGIN_JAR)).toURI()),
            pluginsDir.resolve("plugin-template-test.jar").toFile()
        );

        Path flowsDir = Files.createDirectory(temp.resolve("flows"));
        Files.writeString(flowsDir.resolve("external.yml"), """
            id: external-plugin-flow
            namespace: io.kestra.tests.flowpath
            tasks:
              - id: example
                type: io.kestra.plugin.templates.ExampleTask
                from: test
            """);

        unregisterExternalType();

        try {
            assertThat(pluginRegistry.findClassByIdentifier(EXTERNAL_TYPE)).isNull();

            List<FlowWithSource> loadedBefore = loader.load(MAIN_TENANT, flowsDir.toFile());

            assertThat(loadedBefore)
                .as("flow is skipped while its plugin type is unregistered (the #19736 failure)")
                .isEmpty();

            pluginRegistry.registerIfAbsent(pluginsDir);

            assertThat(pluginRegistry.findClassByIdentifier(EXTERNAL_TYPE)).isNotNull();

            List<FlowWithSource> loadedAfter = loader.load(MAIN_TENANT, flowsDir.toFile());

            assertThat(loadedAfter)
                .as("flow loads through the --flow-path code path once plugins are registered")
                .hasSize(1);
            assertThat(flowRepository.findById(MAIN_TENANT, "io.kestra.tests.flowpath", "external-plugin-flow")).isPresent();
        } finally {
            flowRepository.findByIdWithSource(MAIN_TENANT, "io.kestra.tests.flowpath", "external-plugin-flow")
                .ifPresent(flowRepository::delete);
            unregisterExternalType();
        }
    }

    private void unregisterExternalType() {
        List<RegisteredPlugin> holding = pluginRegistry.plugins(
            plugin -> plugin.allClass().stream().anyMatch(clazz -> clazz.getName().equals(EXTERNAL_TYPE))
        );
        pluginRegistry.unregister(holding);
    }
}
