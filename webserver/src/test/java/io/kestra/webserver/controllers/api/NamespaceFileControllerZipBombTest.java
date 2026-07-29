package io.kestra.webserver.controllers.api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import io.kestra.core.utils.TestsUtils;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@code /namespaces/{namespace}/files} rejects a ZIP archive violating the
 * configured ZIP-bomb protection limits with an HTTP 422, instead of decompressing it.
 */
@MicronautTest
@Property(name = "kestra.security.zip-bomb-protection.enabled", value = "true")
@Property(name = "kestra.security.zip-bomb-protection.max-number-of-entries", value = "1000")
@Property(name = "kestra.security.zip-bomb-protection.max-entry-size", value = "10")
class NamespaceFileControllerZipBombTest {

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldRejectZipWithEntryLargerThanConfiguredLimit() throws IOException {
        // Given a ZIP archive with a single entry larger than the configured max entry size
        String namespace = TestsUtils.randomNamespace();
        File zip = File.createTempFile("namespace-files-bomb", ".zip");
        try (
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream archive = new ZipOutputStream(bos)
        ) {
            archive.putNextEntry(new ZipEntry("oversized.txt"));
            archive.write("this content is way larger than the ten byte configured limit".getBytes());
            archive.closeEntry();
            archive.finish();
            Files.write(zip.toPath(), bos.toByteArray());
        }

        var body = MultipartBody.builder()
            .addPart("fileContent", "files.zip", zip)
            .build();

        // When uploading that archive
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/main/namespaces/" + namespace + "/files?path=/ignored.zip", body)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
            )
        );

        // Then the upload is rejected with a 422, naming the violated ZIP-bomb protection limit
        assertThat(exception.getStatus().getCode()).isEqualTo(422);
        assertThat(exception.getResponse().getBody(String.class).orElse(""))
            .contains("kestra.security.zip-bomb-protection.max-entry-size");

        zip.delete();
    }
}
