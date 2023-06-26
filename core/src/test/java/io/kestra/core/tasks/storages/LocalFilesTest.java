package io.kestra.core.tasks.storages;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class LocalFilesTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        var runContext = runContextFactory.of(Map.of("toto", "tata"));
        var existingFile = Files.createFile(Path.of(runContext.tempDir().toString(), "hello-output.txt"));
        Files.write(existingFile, "Hello Output".getBytes());
        var resource = ConcatTest.class.getClassLoader().getResource("application.yml");
        var storageFile = storageInterface.put(
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        var task = LocalFiles.builder()
            .id(IdUtils.create())
            .type(LocalFiles.class.getName())
            .inputs(List.of(
                LocalFiles.LocalFile.builder().name("hello-input.txt").content("Hello Input").build(),
                LocalFiles.LocalFile.builder().name("execution.txt").content("{{toto}}").build(),
                LocalFiles.LocalFile.builder().name("application.yml").content(storageFile.toString()).build())
            )
            .outputs(List.of("hello-output.txt"))
            .build();
        var outputs = task.run(runContext);

        assertThat(outputs, notNullValue());
        assertThat(outputs.getOutputFiles(), notNullValue());
        assertThat(outputs.getOutputFiles().size(), is(1));
        assertThat(
            new String(storageInterface.get(outputs.getOutputFiles().get(0)).readAllBytes()),
            is("Hello Output")
        );
        assertThat(runContext.tempDir().toFile().list().length, is(3));
        assertThat(Files.readString(runContext.tempDir().resolve("hello-input.txt")), is("Hello Input"));
        assertThat(Files.readString(runContext.tempDir().resolve("execution.txt")), is("tata"));
        assertThat(Files.readString(runContext.tempDir().resolve("application.yml")),
            is(new String(storageInterface.get(storageFile).readAllBytes())));

        runContext.cleanup();
    }

    @Test
    void failWithExistingInputFile() throws IOException {
        var runContext = runContextFactory.of();
        Files.createFile(Path.of(runContext.tempDir().toString(), "hello-input.txt"));

        var task = LocalFiles.builder()
            .id(IdUtils.create())
            .type(LocalFiles.class.getName())
            .inputs(List.of(
                LocalFiles.LocalFile.builder().name("hello-input.txt").content("Hello Input").build(),
                LocalFiles.LocalFile.builder().name("execution.txt").content("{{toto}}").build()
            ))
            .build();

        assertThrows(IllegalVariableEvaluationException.class, () -> task.run(runContext));
    }

    @Test
    void failWithMissingOutputFile() {
        var runContext = runContextFactory.of();

        var task = LocalFiles.builder()
            .id(IdUtils.create())
            .type(LocalFiles.class.getName())
            .outputs(List.of("hello-output.txt"))
            .build();

        assertThrows(IllegalVariableEvaluationException.class, () -> task.run(runContext));
    }
}
