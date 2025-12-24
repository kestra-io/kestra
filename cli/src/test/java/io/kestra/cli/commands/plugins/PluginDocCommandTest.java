package io.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class PluginDocCommandTest {

    public static final String PLUGIN_TEMPLATE_TEST = "plugin-template-test-0.24.0-SNAPSHOT.jar";

    @Test
    void run() throws IOException, URISyntaxException {
        var testDirectoryName = PluginListCommandTest.class.getSimpleName();
        Path pluginsPath = Files.createTempDirectory(testDirectoryName + "_pluginsPath_");
        pluginsPath.toFile().deleteOnExit();

        FileUtils.copyFile(
            new File(Objects.requireNonNull(PluginListCommandTest.class.getClassLoader()
                .getResource("plugins/" + PLUGIN_TEMPLATE_TEST)).toURI()),
            new File(URI.create("file://" + pluginsPath.toAbsolutePath() + "/" + PLUGIN_TEMPLATE_TEST))
        );

        Path docPath = Files.createTempDirectory(testDirectoryName + "_docPath_");
        docPath.toFile().deleteOnExit();

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"--plugins", pluginsPath.toAbsolutePath().toString(), docPath.toAbsolutePath().toString()};
            PicocliRunner.call(PluginDocCommand.class, ctx, args);

            List<Path> files = Files.list(docPath).toList();

            assertThat(files.stream().map(path -> path.getFileName().toString())).contains("plugin-template-test");
            // don't know why, but sometimes there is an addition "plugin-notifications" directory present
            var directory = files.stream().filter(path -> "plugin-template-test".equals(path.getFileName().toString())).findFirst().get().toFile();
            assertThat(directory.isDirectory()).isTrue();
            assertThat(directory.listFiles().length).isEqualTo(3);

            var readme = directory.toPath().resolve("index.md");
            var readmeContent = new String(Files.readAllBytes(readme));

            assertThat(readmeContent).contains("""
                ---
                title: Template test
                description: "Plugin template for Kestra"
                editLink: false

                ---
                # Template test
                """);

            assertThat(readmeContent).contains("""
                Plugin template for Kestra

                This is a more complex description of the plugin.

                This is in markdown and will be inline inside the plugin page.
                """);

            assertThat(readmeContent).contains("""
                    /> Subgroup title

                    Subgroup description


                    ### Tasks
                    * [ExampleTask](./tasks/io.kestra.plugin.templates.ExampleTask.md)




                    ## Guides
                    * [Authentication](./guides/authentication.md)
                       \s
                    * [Reporting](./guides/reporting.md)
                       \s
                    """);

            // check @PluginProperty from an interface
            var task = directory.toPath().resolve("tasks/io.kestra.plugin.templates.ExampleTask.md");
            String taskDoc = new String(Files.readAllBytes(task));
            assertThat(taskDoc).contains("""
                ### `example`
                * **Type:** ==string==
                * **Dynamic:** ✔️
                * **Required:** ❌

                **Example interface**
                """);
            assertThat(taskDoc).contains("""
                ### `from`
                * **Type:**
                  * ==string==
                  * ==array==
                  * [==Example==](#io.kestra.core.models.annotations.example)
                * **Dynamic:** ✔️
                * **Required:** ✔️
                """);

            var authenticationGuide = directory.toPath().resolve("guides/authentication.md");
            assertThat(new String(Files.readAllBytes(authenticationGuide))).contains("This is how to authenticate for this plugin:");
            var reportingGuide = directory.toPath().resolve("guides/reporting.md");
            assertThat(new String(Files.readAllBytes(reportingGuide))).contains("This is the reporting of the plugin:");
        }
    }
}