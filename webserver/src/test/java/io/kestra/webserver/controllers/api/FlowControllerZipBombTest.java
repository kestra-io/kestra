package io.kestra.webserver.controllers.api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

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
 * Verifies that {@code /flows/import} rejects a ZIP archive violating the configured
 * ZIP-bomb protection limits with an HTTP 422, instead of decompressing it.
 */
@MicronautTest
@Property(name = "kestra.security.zip-bomb-protection.enabled", value = "true")
@Property(name = "kestra.security.zip-bomb-protection.max-number-of-entries", value = "2")
@Property(name = "kestra.security.zip-bomb-protection.max-entry-size", value = "1000")
class FlowControllerZipBombTest {

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldRejectZipWithTooManyEntries() throws IOException {
        // Given a ZIP archive with more entries than the configured limit allows
        File zip = File.createTempFile("flows-bomb", ".zip");
        try (
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream archive = new ZipOutputStream(bos)
        ) {
            for (int i = 0; i < 3; i++) {
                archive.putNextEntry(new ZipEntry("flow-" + i + ".yaml"));
                archive.write(("id: flow-" + i + "\nnamespace: io.kestra.tests\ntasks:\n  - id: t\n    type: io.kestra.plugin.core.log.Log\n    message: hello").getBytes());
                archive.closeEntry();
            }
            archive.finish();
            Files.write(zip.toPath(), bos.toByteArray());
        }

        var body = MultipartBody.builder()
            .addPart("fileUpload", "flows.zip", zip)
            .build();

        // When importing that archive
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/main/flows/import", body).contentType(MediaType.MULTIPART_FORM_DATA)
            )
        );

        // Then the import is rejected with a 422, naming the violated ZIP-bomb protection limit
        assertThat(exception.getStatus().getCode()).isEqualTo(422);
        assertThat(exception.getResponse().getBody(String.class).orElse(""))
            .contains("kestra.security.zip-bomb-protection.max-number-of-entries");

        zip.delete();
    }
}
