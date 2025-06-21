package io.kestra.core.models.property;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class URIFetcherTest {
    @Inject
    private StorageInterface storage;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void supports() {
        assertThat(URIFetcher.supports("kestra://something.something")).isTrue();
        assertThat(URIFetcher.supports("file:///path/something.something")).isTrue();
        assertThat(URIFetcher.supports("Some kestra stuff")).isFalse();
    }

    @Test
    void shouldFetchFromInternalStorage() throws URISyntaxException, IOException {
        URI uri = storageUpload();
        RunContext runContext = runContextFactory.of();

        try(var fetched = URIFetcher.of(uri).fetch(runContext)) {
            String str = new String(fetched.readAllBytes());
            assertThat(str).isEqualTo("Hello World");
        }
    }

    @Test
    void shouldFailToFetchFromLocalFileWhenNotAllowed() throws IOException {
        URI uri = createFile();
        RunContext runContext = runContextFactory.of();

        assertThrows(SecurityException.class, () -> {
            try(var ignored = URIFetcher.of(uri).fetch(runContext)) {}
        });
    }

    @Test
    @Property(name = "kestra.plugins.allowed-paths", value = "/tmp")
    void shouldFetchFromLocalFileWhenAllowedGlobally() throws IOException {
        URI uri = createFile();
        RunContext runContext = runContextFactory.of();

        try (var fetch = URIFetcher.of(uri).fetch(runContext)) {
            String fetchedContent = new String(fetch.readAllBytes());
            assertThat(fetchedContent).isEqualTo("Hello World");
        }
    }

    @Test
    void shouldFetchFromLocalFileWhenAllowedForPlugin() throws IOException {
        URI uri = createFile();
        RunContext runContext = Mockito.spy(runContextFactory.of());
        Mockito.when(runContext.pluginConfiguration(Mockito.anyString())).thenReturn(Optional.of(List.of("/tmp")));

        try (var fetch = URIFetcher.of(uri).fetch(runContext)) {
            String fetchedContent = new String(fetch.readAllBytes());
            assertThat(fetchedContent).isEqualTo("Hello World");
        }
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
}