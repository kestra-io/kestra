package io.kestra.core.models.script;

import io.kestra.core.runners.RunContextFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class ScriptServiceTest {
    @Inject private RunContextFactory runContextFactory;

    @Test
    void replaceInternalStorage() throws IOException {
        var runContext = runContextFactory.of();
        var command  = ScriptService.replaceInternalStorage(runContext, null);
        assertThat(command, is(""));

        command = ScriptService.replaceInternalStorage(runContext, "my command");
        assertThat(command, is("my command"));

        Path path = Path.of("/tmp/unittest/file.txt");
        if (!path.toFile().exists()) {
            Files.createFile(path);
        }
        command = ScriptService.replaceInternalStorage(runContext, "my command with a file: kestra://some/file.txt");
        assertThat(command, startsWith("my command with a file: /tmp/"));
        path.toFile().delete();
    }

    @Test
    void uploadInputFiles() throws IOException {
        var runContext = runContextFactory.of();
        Path path = Path.of("/tmp/unittest/file.txt");
        if (!path.toFile().exists()) {
            Files.createFile(path);
        }

        var commands  = ScriptService.uploadInputFiles(runContext, List.of("my command with a file: kestra://some/file.txt"));
        assertThat(commands, not(empty()));
        assertThat(commands.get(0), startsWith("my command with a file: /tmp/"));
        path.toFile().delete();
    }

    @Test
    void uploadOutputFiles() throws IOException {
        var runContext = runContextFactory.of();
        Path path = Path.of("/tmp/unittest/file.txt");
        if (!path.toFile().exists()) {
            Files.createFile(path);
        }

        var outputFiles = ScriptService.uploadOutputFiles(runContext, Path.of("/tmp/unittest"));
        assertThat(outputFiles, not(anEmptyMap()));
        assertThat(outputFiles.get("file.txt"), is(URI.create("kestra:///file.txt")));

        path.toFile().delete();
    }

    @Test
    void scriptCommands() {
        var scriptCommands = ScriptService.scriptCommands(List.of("interpreter"), List.of("beforeCommand"), List.of("command"));
        assertThat(scriptCommands, hasSize(2));
        assertThat(scriptCommands.get(0), is("interpreter"));
        assertThat(scriptCommands.get(1), is("beforeCommand\ncommand"));
    }
}