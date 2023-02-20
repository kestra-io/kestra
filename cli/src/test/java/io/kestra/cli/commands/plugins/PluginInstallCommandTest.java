package io.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.kestra.core.contexts.KestraClassLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PluginInstallCommandTest {
    @BeforeAll
    static void init() {
        if (!KestraClassLoader.isInit()) {
            KestraClassLoader.create(PluginInstallCommandTest.class.getClassLoader());
        }
    }

    @Test
    void run() throws IOException {
        Path pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), "io.kestra.plugin:plugin-notifications:0.6.0"};
            PicocliRunner.call(PluginInstallCommand.class, ctx, args);

            List<Path> files = Files.list(pluginsPath).collect(Collectors.toList());

            assertThat(files.size(), is(1));
            assertThat(files.get(0).getFileName().toString(), is("plugin-notifications-0.6.0.jar"));
        }
    }
}
