package org.kestra.cli.commands.plugins;

import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class PluginInstallCommandTest {
    @Inject
    PluginInstallCommand pluginInstallCommand;

    @Test
    void run() throws IOException {
        pluginInstallCommand.pluginsPath = Files.createTempDirectory(PluginInstallCommandTest.class.getSimpleName());
        pluginInstallCommand.dependencies = Collections.singletonList("org.kestra.task.notifications:task-notifications:0.1.0");

        pluginInstallCommand.run();

        List<Path> files = Files.list(pluginInstallCommand.pluginsPath).collect(Collectors.toList());

        assertThat(files.size(), is(1));
        assertThat(files.get(0).getFileName().toString(), is("task-notifications-0.1.0.jar"));
   }
}