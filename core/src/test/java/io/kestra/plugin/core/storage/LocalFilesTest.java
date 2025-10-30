package io.kestra.plugin.core.storage;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.TestsUtils;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("deprecation")
@KestraTest
class LocalFilesTest {
    @Inject
    TestRunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    private URI internalFiles(String tenantId) throws IOException, URISyntaxException {
        var resource = ConcatTest.class.getClassLoader().getResource("application-test.yml");

        return storageInterface.put(
            tenantId,
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );
    }


    @Test
    void run() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var runContext = runContextFactory.of("namesapce", tenant, Map.of("toto", "tata"));
        var storageFile = internalFiles(tenant);

        var task = LocalFiles.builder()
            .id(IdUtils.create())
            .type(LocalFiles.class.getName())
            .inputs(Map.of(
                "hello-input.txt", "Hello Input",
                "execution.txt", "{{toto}}",
                "application-test.yml", storageFile.toString()
            ))
            .outputs(Property.ofValue(List.of("hello-input.txt")))
            .build();
        var outputs = task.run(runContext);

        assertThat(outputs).isNotNull();
        assertThat(outputs.getUris()).isNotNull();
        assertThat(outputs.getUris().size()).isEqualTo(1);
        assertThat(new String(storageInterface.get(tenant, null, outputs.getUris().get("hello-input.txt")).readAllBytes())).isEqualTo("Hello Input");
        assertThat(runContext.workingDir().path().toFile().list().length).isEqualTo(2);
        assertThat(Files.readString(runContext.workingDir().path().resolve("execution.txt"))).isEqualTo("tata");
        assertThat(Files.readString(runContext.workingDir().path().resolve("application-test.yml"))).isEqualTo(new String(storageInterface.get(tenant, null, storageFile).readAllBytes()));

        runContext.cleanup();
    }

    @Test
    void recursive() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        var runContext = runContextFactory.of("namesapce", tenant, Map.of("toto", "tata"));
        var storageFile = internalFiles(tenant);

        var task = LocalFiles.builder()
            .id(IdUtils.create())
            .type(LocalFiles.class.getName())
            .inputs(Map.of(
                "test/hello-input.txt", "Hello Input",
                "test/sub/dir/2/execution.txt", "{{toto}}",
                "test/sub/dir/3/application-test.yml", storageFile.toString()
            ))
            .outputs(Property.ofValue(List.of("test/**")))
            .build();
        var outputs = task.run(runContext);

        assertThat(outputs).isNotNull();
        assertThat(outputs.getUris()).isNotNull();
        assertThat(outputs.getUris().size()).isEqualTo(3);
        assertThat(new String(storageInterface.get(tenant, null, outputs.getUris().get("test/hello-input.txt")).readAllBytes())).isEqualTo("Hello Input");
        assertThat(new String(storageInterface.get(tenant, null, outputs.getUris().get("test/sub/dir/2/execution.txt"))
            .readAllBytes())).isEqualTo("tata");
        assertThat(new String(storageInterface.get(tenant, null, outputs.getUris().get("test/sub/dir/3/application-test.yml"))
            .readAllBytes())).isEqualTo(new String(storageInterface.get(tenant, null, storageFile).readAllBytes()));
        runContext.cleanup();
    }

    @Test
    void failWithExistingInputFile() throws IOException {
        var runContext = runContextFactory.of();
        Files.createFile(Path.of(runContext.workingDir().path().toString(), "hello-input.txt"));

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
