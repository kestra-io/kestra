package io.kestra.cli.commands.servers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.cli.Kestra;
import io.kestra.core.migration.MigrationRunnerInterface;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.Await;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

class StandAloneCommandTest {
    private static final String PLUGIN_JAR = "plugins/plugin-template-test-0.24.0-SNAPSHOT.jar";
    private static final String EXTERNAL_TYPE = "io.kestra.plugin.templates.ExampleTask";

    @Test
    void shouldLoadFlowPathAfterPluginsAreRegistered(@TempDir Path temp) throws Exception {
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

        Path config = Files.writeString(temp.resolve("kestra.yml"), """
            kestra:
              plugins:
                auto-install:
                  enabled: false
            """);

        String[] serverArgs = {
            "server", "standalone",
            "--plugins", pluginsDir.toString(),
            "--flow-path", flowsDir.toString(),
            "--worker-thread", "0",
            "--no-indexer",
            "--no-controller",
            "-c", config.toString()
        };

        try (ApplicationContext ctx = Kestra.applicationContext(Kestra.class, new String[] { Environment.CLI, Environment.TEST }, serverArgs)) {
            ctx.start();
            ctx.getBean(MigrationRunnerInterface.class).runAlways();

            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);

            String[] standaloneArgs = {
                "--plugins", pluginsDir.toString(),
                "--flow-path", flowsDir.toString(),
                "--worker-thread", "0",
                "--no-indexer",
                "--no-controller",
                "-c", config.toString()
            };

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> PicocliRunner.call(StandAloneCommand.class, ctx, standaloneArgs));

                Await.await().atMost(Duration.ofSeconds(30)).until(
                    () -> flowRepository.findById(MAIN_TENANT, "io.kestra.tests.flowpath", "external-plugin-flow").isPresent()
                );

                PluginRegistry pluginRegistry = ctx.getBean(PluginRegistry.class);
                pluginRegistry.unregister(
                    pluginRegistry.plugins(
                        plugin -> plugin.allClass().stream().anyMatch(clazz -> clazz.getName().equals(EXTERNAL_TYPE))
                    )
                );
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
