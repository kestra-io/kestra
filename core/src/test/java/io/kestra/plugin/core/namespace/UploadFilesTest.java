package io.kestra.plugin.core.namespace;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.NamespaceFilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;


@KestraTest
public class UploadFilesTest {
    @Inject
    StorageInterface storageInterface;

    @Inject
    NamespaceFilesService namespaceFilesService;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void uploadConflictError() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();
        File file = new File(Objects.requireNonNull(UploadFilesTest.class.getClassLoader().getResource("application-test.yml")).toURI());

        URI fileStorage = storageInterface.put(
            null,
            new URI("/" + FriendlyId.createFriendlyId()),
            new FileInputStream(file)
        );
        UploadFiles uploadFile = UploadFiles.builder()
            .id(UploadFiles.class.getSimpleName())
            .type(UploadFiles.class.getName())
            .files(Map.of("/path/file.txt", fileStorage.toString()))
            .namespace(namespace)
            .conflict(UploadFiles.ConflictAction.ERROR)
            .destination("/folder")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, uploadFile, ImmutableMap.of());
        uploadFile.run(runContext);

        assertThat(namespaceFilesService.recursiveList(null, namespace, URI.create("")).size(), is(1));

        assertThrows(RuntimeException.class, () -> {
            uploadFile.run(runContext);
        });
    }

    @Test
    void uploadConflictOverwrite() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();

        URI fileStorage = addToStorage("application-test.yml");

        UploadFiles uploadFile = UploadFiles.builder()
            .id(UploadFiles.class.getSimpleName())
            .type(UploadFiles.class.getName())
            .files(Map.of("/path/file.txt", fileStorage.toString()))
            .namespace("{{ inputs.namespace }}")
            .destination("/folder")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, uploadFile,  ImmutableMap.of("namespace", namespace));
        uploadFile.run(runContext);

        List<URI> namespaceFiles = namespaceFilesService.recursiveList(null, namespace, URI.create(""));
        assertThat(namespaceFiles.size(), is(1));

        String previousFile = IOUtils.toString(namespaceFilesService.content(null, namespace, namespaceFiles.getFirst()), StandardCharsets.UTF_8);

        fileStorage = addToStorage("logback.xml");
        uploadFile = uploadFile.toBuilder()
                .files(Map.of("/path/file.txt", fileStorage.toString()))
                .build();

        uploadFile.run(runContext);

        namespaceFiles = namespaceFilesService.recursiveList(null, namespace, URI.create(""));
        assertThat(namespaceFiles.size(), is(1));

        String newFile = IOUtils.toString(namespaceFilesService.content(null, namespace, namespaceFiles.getFirst()), StandardCharsets.UTF_8);

        assertThat(previousFile.equals(newFile), is(false));
    }

    @Test
    void uploadConflictSkip() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();

        URI fileStorage = addToStorage("application-test.yml");

        UploadFiles uploadFile = UploadFiles.builder()
            .id(UploadFiles.class.getSimpleName())
            .type(UploadFiles.class.getName())
            .files(Map.of("/path/file.txt", fileStorage.toString()))
            .namespace(namespace)
            .conflict(UploadFiles.ConflictAction.SKIP)
            .destination("/folder")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, uploadFile, ImmutableMap.of());
        uploadFile.run(runContext);

        List<URI> namespaceFiles = namespaceFilesService.recursiveList(null, namespace, URI.create(""));
        assertThat(namespaceFiles.size(), is(1));

        String previousFile = IOUtils.toString(namespaceFilesService.content(null, namespace, namespaceFiles.getFirst()), StandardCharsets.UTF_8);

        fileStorage = addToStorage("logback.xml");
        uploadFile = uploadFile.toBuilder()
            .files(Map.of("/path/file.txt", fileStorage.toString()))
            .build();

        uploadFile.run(runContext);

        namespaceFiles = namespaceFilesService.recursiveList(null, namespace, URI.create(""));
        assertThat(namespaceFiles.size(), is(1));

        String newFile = IOUtils.toString(namespaceFilesService.content(null, namespace, namespaceFiles.getFirst()), StandardCharsets.UTF_8);

        assertThat(previousFile.equals(newFile), is(true));
    }

    private URI addToStorage(String fileToLoad) throws IOException, URISyntaxException {
        File file = new File(Objects.requireNonNull(UploadFilesTest.class.getClassLoader().getResource(fileToLoad)).toURI());

        return storageInterface.put(
            null,
            new URI("/" + FriendlyId.createFriendlyId()),
            new FileInputStream(file)
        );
    }
}
