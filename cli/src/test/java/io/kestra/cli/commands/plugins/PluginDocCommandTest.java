package io.kestra.cli.commands.plugins;

import io.kestra.core.contexts.KestraClassLoader;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PluginDocCommandTest {
    @BeforeAll
    static void init() {
        if (!KestraClassLoader.isInit()) {
            KestraClassLoader.create(PluginDocCommandTest.class.getClassLoader());
        }
    }

    @Test
    void run() throws IOException, URISyntaxException {
        Path pluginsPath = Files.createTempDirectory(PluginListCommandTest.class.getSimpleName());
        pluginsPath.toFile().deleteOnExit();

        FileUtils.copyFile(
            new File(Objects.requireNonNull(PluginListCommandTest.class.getClassLoader()
                .getResource("plugins/plugin-template-test-0.6.0-SNAPSHOT.jar")).toURI()),
            new File(URI.create("file://" + pluginsPath.toAbsolutePath() + "/plugin-template-test-0.6.0-SNAPSHOT.jar"))
        );

        Path docPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        docPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), docPath.toAbsolutePath().toString()};
            PicocliRunner.call(PluginDocCommand.class, ctx, args);

            List<Path> files = Files.list(docPath).collect(Collectors.toList());

            assertThat(files.size(), is(1));
            assertThat(files.get(0).getFileName().toString(), is("plugin-template-test"));
            var directory = files.get(0).toFile();
            assertThat(directory.isDirectory(), is(true));
            assertThat(directory.listFiles().length, is(2));
            var readme = directory.toPath().resolve("README.md");
            var task = directory.toPath().resolve("tasks/io.kestra.plugin.templates.ExampleTask.md");
            assertThat(new String(Files.readAllBytes(readme)), is("---\n" +
                "title: Plugin template test\n" +
                "editLink: false\n" +
                "---\n" +
                "# Plugin template test\n" +
                "\n" +
                "Plugin template for Kestra\n" +
                "## Subgroup title\n" +
                "    \n" +
                "Subgroup description\n" +
                "### Tasks\n" +
                "\n" +
                "* [ExampleTask](tasks/templates/io.kestra.plugin.templates.ExampleTask.html)\n"));

            // check @PluginProperty from an interface
            assertThat(new String(Files.readAllBytes(task)), containsString("### `example`\n" +
                "\n" +
                "* **Type:** ==string==\n" +
                "* **Dynamic:** ✔️\n" +
                "* **Required:** ❌\n" +
                "\n" +
                "\n" +
                "\n" +
                "> Example interface\n"));
        }
    }
}