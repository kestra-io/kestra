package io.kestra.cli.commands.plugins;

import io.kestra.core.contexts.KestraClassLoader;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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
                .getResource("plugins/plugin-template-test-0.15.0-SNAPSHOT.jar")).toURI()),
            new File(URI.create("file://" + pluginsPath.toAbsolutePath() + "/plugin-template-test-0.15.0-SNAPSHOT.jar"))
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
            assertThat(directory.listFiles().length, is(3));

            var readme = directory.toPath().resolve("index.md");
            assertThat(new String(Files.readAllBytes(readme)), containsString("""
                ---
                title: Template test
                description: "Plugin template for Kestra"
                editLink: false

                ---
                # Template test

                Plugin template for Kestra

                This is a more complex description of the plugin.

                This is in markdown and will be inline inside the plugin page.
                """));
            assertThat(new String(Files.readAllBytes(readme)), containsString(
                """
                    /> Subgroup title

                    Subgroup description


                    ### Tasks
                    * [ExampleTask](./tasks/io.kestra.plugin.templates.ExampleTask.md)




                    ## Guides
                    * [Authentication](./guides/authentication.md)
                       \s
                    * [Reporting](./guides/reporting.md)
                       \s
                    """));

            // check @PluginProperty from an interface
            var task = directory.toPath().resolve("tasks/io.kestra.plugin.templates.ExampleTask.md");
            String taskDoc = new String(Files.readAllBytes(task));
            assertThat(taskDoc, containsString("""
                ### `example`
                * **Type:** ==string==
                * **Dynamic:** ✔️
                * **Required:** ❓

                > Example interface
                """));
            assertThat(taskDoc, containsString("""
                ### `from`
                * **Type:**
                  * ==string==
                  * ==array==
                  * [==io.kestra.core.models.annotations.Example==](#io.kestra.core.models.annotations.example)
                * **Dynamic:** ✔️
                * **Required:** ✔️
                """));

            var authenticationGuide = directory.toPath().resolve("guides/authentication.md");
            assertThat(new String(Files.readAllBytes(authenticationGuide)), containsString("This is how to authenticate for this plugin:"));
            var reportingGuide = directory.toPath().resolve("guides/reporting.md");
            assertThat(new String(Files.readAllBytes(reportingGuide)), containsString("This is the reporting of the plugin:"));
        }
    }
}