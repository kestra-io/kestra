package io.kestra.cli.commands.servers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import io.kestra.cli.Kestra;
import io.kestra.core.migration.MigrationRunnerInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.server.Service;
import io.kestra.core.utils.Await;
import io.kestra.executor.DefaultExecutor;
import io.kestra.scheduler.DefaultScheduler;
import io.kestra.worker.systemworker.SystemWorker;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

class StandAloneCommandTest {
    private static final String PLUGIN_JAR = "plugins/plugin-template-test-0.24.0-SNAPSHOT.jar";

    @Test
    void shouldLoadFlowPathAfterPluginsAreRegistered() throws Exception {
        Path temp = Files.createTempDirectory(StandAloneCommandTest.class.getSimpleName());
        temp.toFile().deleteOnExit();

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
                Future<Integer> future = executor.submit(() -> PicocliRunner.call(StandAloneCommand.class, ctx, standaloneArgs));

                Await.await().atMost(Duration.ofSeconds(90)).until(
                    () -> flowRepository.findById(MAIN_TENANT, "io.kestra.tests.flowpath", "external-plugin-flow").isPresent()
                );

                assertThat(flowRepository.findById(MAIN_TENANT, "io.kestra.tests.flowpath", "external-plugin-flow")).isPresent();

                DefaultExecutor defaultExecutor = ctx.getBean(DefaultExecutor.class);
                DefaultScheduler scheduler = ctx.getBean(DefaultScheduler.class);
                SystemWorker systemWorker = ctx.getBean(SystemWorker.class);
                Await.await().atMost(Duration.ofSeconds(120)).until(
                    () -> isStarted(defaultExecutor) && isStarted(scheduler) && isStarted(systemWorker)
                );

                ctx.stop();

                assertThat(future.get(60, TimeUnit.SECONDS)).isZero();
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static boolean isStarted(Service service) {
        return service.getState() == Service.ServiceState.RUNNING || service.getState() == Service.ServiceState.MAINTENANCE;
    }
}
