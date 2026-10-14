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
@Property(name = "kestra.webserver.html-head", value = UiControllerHtmlHeadTest.NON_ASCII_HTML_HEAD)
class UiControllerHtmlHeadTest {
    // Contains characters whose UTF-8 byte length differs from the character count.
    static final String NON_ASCII_HTML_HEAD = "<meta name=\"description\" content=\"héllo wörld — 日本語\">";

    @Inject
    EmbeddedServer embeddedServer;

    @Test
    void shouldUseUtf8ByteLengthForContentLengthWhenHtmlHeadContainsNonAscii() throws Exception {
        // When - identity encoding so the body length is the raw HTML byte length
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(embeddedServer.getURL().toString() + "/ui/")).build(),
            HttpResponse.BodyHandlers.ofByteArray()
        );

        // Then
        String html = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(html).contains(NON_ASCII_HTML_HEAD);
        assertThat(response.body().length).isGreaterThan(html.length());
        assertThat(Long.parseLong(response.headers().firstValue("Content-Length").orElseThrow()))
            .isEqualTo(response.body().length);
    }
}
