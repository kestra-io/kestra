package io.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

class PluginListCommandTest {

    private static final String PLUGIN_TEMPLATE_TEST = "plugin-template-test-0.17.0-SNAPSHOT.jar";

    @Test
    void run() throws IOException, URISyntaxException {
        Path pluginsPath = Files.createTempDirectory(PluginListCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        FileUtils.copyFile(
            new File(Objects.requireNonNull(PluginListCommandTest.class.getClassLoader()
                .getResource("plugins/" + PLUGIN_TEMPLATE_TEST)).toURI()),
            new File(URI.create("file://" + pluginsPath.toAbsolutePath() + "/" + PLUGIN_TEMPLATE_TEST))
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString()};
            PicocliRunner.call(PluginListCommand.class, ctx, args);

            assertThat(out.toString(), containsString("io.kestra.plugin.templates.Example"));
        }
    }
}
