package io.kestra.core.models.property;

import io.kestra.core.runners.*;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class URIFetcherTest {
    @Inject
    private StorageInterface storage;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void supports() {
        assertThat(URIFetcher.supports("kestra://something.something")).isTrue();
        assertThat(URIFetcher.supports("file:///path/something.something")).isTrue();
        assertThat(URIFetcher.supports("Some kestra stuff")).isFalse();
    }

    @Test
    void shouldFetchFromInternalStorage() throws URISyntaxException, IOException {
        URI uri = storageUpload();
        RunContext runContext = buildRunContext();

        try(var fetched = URIFetcher.of(uri).fetch(runContext)) {
            String str = new String(fetched.readAllBytes());
            assertThat(str).isEqualTo("Hello World");
        }
    }

    @Test
    void shouldFailToFetchFromLocalFileWhenNotAllowed() throws IOException {
        URI uri = createFile();
        RunContext runContext = buildRunContext();

        assertThrows(SecurityException.class, () -> {
            try(var ignored = URIFetcher.of(uri).fetch(runContext)) {}
        });
    }

    @Test
    void shouldFetchFromLocalFileWhenAllowedGlobally() throws IOException {
        URI uri = createFile();
        RunContext runContext = buildRunContext(List.of("/tmp"));

        try (var fetch = URIFetcher.of(uri).fetch(runContext)) {
            String fetchedContent = new String(fetch.readAllBytes());
            assertThat(fetchedContent).isEqualTo("Hello World");
        }
    }

    @Test
    void shouldFetchFromLocalFileWhenAllowedForPlugin() throws IOException {
        URI uri = createFile();
        RunContext runContext = buildRunContext(Collections.emptyList(), List.of("/tmp"));

        try (var fetch = URIFetcher.of(uri).fetch(runContext)) {
            String fetchedContent = new String(fetch.readAllBytes());
            assertThat(fetchedContent).isEqualTo("Hello World");
        }
    }

    @Test
    void shouldFetchFromNsfile() throws IOException, URISyntaxException {
        String namespace = IdUtils.create();
        URI uri = createNsFile(namespace, false);
        RunContext runContext = runContextFactory.of(Map.of("flow", Map.of("namespace", namespace)));

        try (var fetch = URIFetcher.of(uri).fetch(runContext)) {
            String fetchedContent = new String(fetch.readAllBytes());
            assertThat(fetchedContent).isEqualTo("Hello World");
        }
    }

    @Test
    void shouldFetchFromNsfileFromOtherNs() throws IOException, URISyntaxException {
        String namespace = IdUtils.create();
        URI uri = createNsFile(namespace, true);
        RunContext runContext = runContextFactory.of(Map.of("flow", Map.of("namespace", "other")));

        try (var fetch = URIFetcher.of(uri).fetch(runContext)) {
            String fetchedContent = new String(fetch.readAllBytes());
            assertThat(fetchedContent).isEqualTo("Hello World");
        }
    }

    private RunContext buildRunContext() {
        return buildRunContext(Collections.emptyList(), Collections.emptyList());
    }

    private RunContext buildRunContext(List<String> globalAllowedPaths) {
        return buildRunContext(globalAllowedPaths, Collections.emptyList());
    }

    private RunContext buildRunContext(List<String> globalAllowedPaths, List<String> pluginAllowedPath) {
        var spy = Mockito.spy(runContextFactory.of());
        var localPath = new LocalPathFactory(globalAllowedPaths).createLocalPath(spy);
        Mockito.when(spy.localPath()).thenReturn(localPath);
        Mockito.when(spy.pluginConfiguration(Mockito.anyString())).thenReturn(Optional.of(pluginAllowedPath));
        return spy;
    }

    private URI createFile() throws IOException {
        File tempFile = File.createTempFile("file", ".txt");
        Files.write(tempFile.toPath(), "Hello World".getBytes());
        return tempFile.toPath().toUri();
    }

    private URI storageUpload() throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        Files.write(tempFile.toPath(), "Hello World".getBytes());

        return storage.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/file.txt"),
            new FileInputStream(tempFile)
        );
    }

    private URI createNsFile(String namespace, boolean nsInAuthority) throws IOException, URISyntaxException {
        String filePath = "file.txt";
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace, storage);
        namespaceStorage.putFile(Path.of("/" + filePath), new ByteArrayInputStream("Hello World".getBytes()));
        return URI.create("nsfile://" + (nsInAuthority ? namespace : "") + "/" + filePath);
    }
}
