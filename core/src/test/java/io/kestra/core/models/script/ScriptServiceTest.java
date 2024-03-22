package io.kestra.core.models.script;

import io.kestra.core.runners.RunContextFactory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

        String internalStorageUri = "kestra://some/file.txt";
        AtomicReference<String> localFile = new AtomicReference<>();
        try {
            command = ScriptService.replaceInternalStorage(runContext, "my command with a file: " + internalStorageUri, (ignored, file) -> localFile.set(file));
            assertThat(command, is("my command with a file: " + localFile.get()));
            assertThat(Path.of(localFile.get()).toFile().exists(), is(true));
        } finally {
            Path.of(localFile.get()).toFile().delete();
            path.toFile().delete();
        }
    }

    @Test
    void uploadInputFiles() throws IOException {
        var runContext = runContextFactory.of();

        Path path = Path.of("/tmp/unittest/file.txt");
        if (!path.toFile().exists()) {
            Files.createFile(path);
        }

        Map<String, String> localFileByInternalStorage = new HashMap<>();
        String internalStorageUri = "kestra://some/file.txt";
        try {
            var commands = ScriptService.uploadInputFiles(runContext, List.of("my command with a file: " + internalStorageUri), localFileByInternalStorage::put);
            assertThat(commands, not(empty()));
            assertThat(commands.get(0), is("my command with a file: " + localFileByInternalStorage.get(internalStorageUri)));
            assertThat(Path.of(localFileByInternalStorage.get(internalStorageUri)).toFile().exists(), is(true));
        } finally {
            localFileByInternalStorage.forEach((k, v) -> Path.of(v).toFile().delete());
            path.toFile().delete();
        }
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