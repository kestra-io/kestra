package io.kestra.webserver.controllers.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "kestra.webserver.ui-resource-cache.max-entry-size", value = "1kb")
class UiControllerMaxEntrySizeTest {
    @Inject
    EmbeddedServer embeddedServer;

    @Test
    void shouldStreamWithoutCachingWhenTheFileExceedsTheEntryBound() throws Exception {
        // When - the ~6KB fixture is above the 1KB per-entry bound
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(embeddedServer.getURL().toString() + "/ui/assets/asset-fixture-abc123.js")).build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );

        // Then - served by the streaming bypass: correct content and cache policy, but no ETag since nothing is cached
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("fixtureLine60");
        assertThat(response.headers().firstValue("Cache-Control")).contains("public, max-age=31536000, immutable");
        assertThat(response.headers().firstValue("ETag")).isEmpty();
    }
}
