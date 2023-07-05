package io.kestra.core.tasks.storages;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class LocalFilesTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    private URI internalFiles() throws IOException, URISyntaxException {
        var resource = ConcatTest.class.getClassLoader().getResource("application.yml");

        return storageInterface.put(
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );
    }


    @Test
    void run() throws Exception {
        var runContext = runContextFactory.of(Map.of("toto", "tata"));
        var storageFile = internalFiles();

        var task = LocalFiles.builder()
            .id(IdUtils.create())
            .type(LocalFiles.class.getName())
            .inputs(Map.of(
                "hello-input.txt", "Hello Input",
                "execution.txt", "{{toto}}",
                "application.yml", storageFile.toString()
            ))
            .outputs(List.of("hello-input.txt"))
            .build();
        var outputs = task.run(runContext);

        assertThat(outputs, notNullValue());
        assertThat(outputs.getUris(), notNullValue());
        assertThat(outputs.getUris().size(), is(1));
        assertThat(
            new String(storageInterface.get(outputs.getUris().get("hello-input.txt")).readAllBytes()),
            is("Hello Input")
        );
        assertThat(runContext.tempDir().toFile().list().length, is(2));
        assertThat(Files.readString(runContext.tempDir().resolve("execution.txt")), is("tata"));
        assertThat(
            Files.readString(runContext.tempDir().resolve("application.yml")),
            is(new String(storageInterface.get(storageFile).readAllBytes()))
        );

        runContext.cleanup();
    }

    @Test
    void recursive() throws Exception {
        var runContext = runContextFactory.of(Map.of("toto", "tata"));
        var storageFile = internalFiles();

        var task = LocalFiles.builder()
            .id(IdUtils.create())
            .type(LocalFiles.class.getName())
            .inputs(Map.of(
                "test/hello-input.txt", "Hello Input",
                "test/sub/dir/2/execution.txt", "{{toto}}",
                "test/sub/dir/3/application.yml", storageFile.toString()
            ))
            .outputs(List.of("test/**"))
            .build();
        var outputs = task.run(runContext);

        assertThat(outputs, notNullValue());
        assertThat(outputs.getUris(), notNullValue());
        assertThat(outputs.getUris().size(), is(3));
        assertThat(
            new String(storageInterface.get(outputs.getUris().get("test/hello-input.txt")).readAllBytes()),
            is("Hello Input")
        );
        assertThat(
            new String(storageInterface.get(outputs.getUris().get("test/sub/dir/2/execution.txt"))
                .readAllBytes()),
            is("tata")
        );
        assertThat(
            new String(storageInterface.get(outputs.getUris().get("test/sub/dir/3/application.yml"))
                .readAllBytes()),
            is(new String(storageInterface.get(storageFile).readAllBytes()))
        );
        runContext.cleanup();
    }

    @Test
    void failWithExistingInputFile() throws IOException {
        var runContext = runContextFactory.of();
        Files.createFile(Path.of(runContext.tempDir().toString(), "hello-input.txt"));

        var task = LocalFiles.builder()
            .id(IdUtils.create())
            .type(LocalFiles.class.getName())
            .inputs(Map.of(
                "hello-input.txt", "Hello Input",
                "execution.txt", "{{toto}}"
            ))
            .build();

        assertThrows(IllegalVariableEvaluationException.class, () -> task.run(runContext));
    }
}
