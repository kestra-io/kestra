package io.kestra.plugin.core.namespace;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micrometer.core.instrument.util.IOUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


@KestraTest
public class UploadFilesTest {
    @Inject
    StorageInterface storageInterface;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldThrowExceptionGivenAlreadyExistingFileWhenConflictError() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();
        File file = new File(Objects.requireNonNull(UploadFilesTest.class.getClassLoader().getResource("application-test.yml")).toURI());

        URI fileStorage = storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/" + FriendlyId.createFriendlyId()),
            new FileInputStream(file)
        );
        UploadFiles uploadFile = UploadFiles.builder()
            .id(UploadFiles.class.getSimpleName())
            .type(UploadFiles.class.getName())
            .filesMap(Map.of("/path/file.txt", fileStorage.toString()))
            .namespace(Property.ofValue(namespace))
            .conflict(Property.ofValue(Namespace.Conflicts.ERROR))
            .destination(Property.ofValue("/folder"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, uploadFile, ImmutableMap.of());
        uploadFile.run(runContext);

        assertThat(runContext.storage().namespace(namespace).all().size()).isEqualTo(1);
        assertThrows(IOException.class, () -> uploadFile.run(runContext));
    }

    @Test
    void  shouldPutFileGivenAlreadyExistingFileWhenConflictOverwrite() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();

        URI fileStorage = addToStorage("application-test.yml");

        UploadFiles uploadFile = UploadFiles.builder()
            .id(UploadFiles.class.getSimpleName())
            .type(UploadFiles.class.getName())
            .filesMap(Map.of("/path/file.txt", fileStorage.toString()))
            .namespace(Property.ofExpression("{{ inputs.namespace }}"))
            .destination(Property.ofValue("/folder"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, uploadFile,  ImmutableMap.of("namespace", namespace));
        uploadFile.run(runContext);

        Namespace namespaceStorage = runContext.storage().namespace(namespace);
        List<NamespaceFile> namespaceFiles = namespaceStorage.all();
        assertThat(namespaceFiles.size()).isEqualTo(1);

        String previousFile = IOUtils.toString(namespaceStorage.getFileContent(Path.of(namespaceFiles.getFirst().path())), StandardCharsets.UTF_8);

        fileStorage = addToStorage("logback.xml");
        uploadFile = uploadFile.toBuilder()
                .filesMap(Map.of("/path/file.txt", fileStorage.toString()))
                .build();

        uploadFile.run(runContext);

        namespaceFiles = namespaceStorage.all();
        assertThat(namespaceFiles.size()).isEqualTo(1);

        String newFile = IOUtils.toString(namespaceStorage.getFileContent(Path.of(namespaceFiles.getFirst().path())), StandardCharsets.UTF_8);

        assertThat(previousFile.equals(newFile)).isFalse();
    }

    @Test
    void shouldPutFileGivenAlreadyExistingFileWhenConflictSkip() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();

        URI fileStorage = addToStorage("application-test.yml");

        UploadFiles uploadFile = UploadFiles.builder()
            .id(UploadFiles.class.getSimpleName())
            .type(UploadFiles.class.getName())
            .filesMap(Map.of("/path/file.txt", fileStorage.toString()))
            .namespace(Property.ofValue(namespace))
            .conflict(Property.ofValue(Namespace.Conflicts.SKIP))
            .destination(Property.ofValue("/folder"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, uploadFile, ImmutableMap.of());
        uploadFile.run(runContext);

        Namespace namespaceStorage = runContext.storage().namespace(namespace);
        List<NamespaceFile> namespaceFiles = namespaceStorage.all();
        assertThat(namespaceFiles.size()).isEqualTo(1);

        String previousFile = IOUtils.toString(namespaceStorage.getFileContent(Path.of(namespaceFiles.getFirst().path())), StandardCharsets.UTF_8);

        fileStorage = addToStorage("logback.xml");
        uploadFile = uploadFile.toBuilder()
            .filesMap(Map.of("/path/file.txt", fileStorage.toString()))
            .build();

        uploadFile.run(runContext);

        namespaceFiles = namespaceStorage.all();
        assertThat(namespaceFiles.size()).isEqualTo(1);

        String newFile = IOUtils.toString(namespaceStorage.getFileContent(Path.of(namespaceFiles.getFirst().path())), StandardCharsets.UTF_8);

        assertThat(previousFile.equals(newFile)).isTrue();
    }

    @Test
    void shouldPutFileFromRegex() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();


        UploadFiles uploadFile = UploadFiles.builder()
            .id(UploadFiles.class.getSimpleName())
            .type(UploadFiles.class.getName())
            .files(Property.ofValue(List.of("glob:**application**")))
            .namespace(Property.ofValue(namespace))
            .conflict(Property.ofValue(Namespace.Conflicts.SKIP))
            .destination(Property.ofValue("/folder/"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, uploadFile, ImmutableMap.of());
        runContext.workingDir().createFile("application-test.yml");
        uploadFile.run(runContext);

        Namespace namespaceStorage = runContext.storage().namespace(namespace);
        List<NamespaceFile> namespaceFiles = namespaceStorage.all();
        assertThat(namespaceFiles.size()).isEqualTo(1);
    }

    private URI addToStorage(String fileToLoad) throws IOException, URISyntaxException {
        File file = new File(Objects.requireNonNull(UploadFilesTest.class.getClassLoader().getResource(fileToLoad)).toURI());

        return storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/" + FriendlyId.createFriendlyId()),
            new FileInputStream(file)
        );
    }
}
