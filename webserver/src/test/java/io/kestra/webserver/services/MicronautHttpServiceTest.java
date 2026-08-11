package io.kestra.webserver.services;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.HttpRequest;

import io.micronaut.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class MicronautHttpServiceTest {
    private static final String URI = "/api/v1/main/executions/webhook/io.kestra.tests/webhook/a-secret-key";

    @Test
    void shouldKeepBodyAsBytesWhenContentTypeIsBinary() {
        // Given
        byte[] content = { (byte) 0xC3, (byte) 0x28, (byte) 0xFF, 0x00 };

        // When
        HttpRequest request = MicronautHttpService.from(
            io.micronaut.http.HttpRequest.POST(URI, content).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
        );

        // Then
        assertThat(request.getBody()).isInstanceOf(HttpRequest.ByteArrayRequestBody.class);
        assertThat((byte[]) request.getBody().getContent()).isEqualTo(content);
        assertThat(request.getBody().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    void shouldDecodeBodyAsStringWhenContentTypeIsTextual() {
        // Given
        String content = "{\"hello\":\"wörld\"}";

        // When
        HttpRequest request = MicronautHttpService.from(
            io.micronaut.http.HttpRequest
                .POST(URI, content.getBytes(StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
        );

        // Then
        assertThat(request.getBody()).isInstanceOf(HttpRequest.StringRequestBody.class);
        assertThat(request.getBody().getContent()).isEqualTo(content);
    }

    @Test
    void shouldDecodeBodyAsStringWhenContentTypeIsFormUrlEncoded() {
        // Given
        String content = "name=john&age=12";

        // When
        HttpRequest request = MicronautHttpService.from(
            io.micronaut.http.HttpRequest
                .POST(URI, content.getBytes(StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        );

        // Then — a form-urlencoded body is text, even though Micronaut does not consider it text based
        assertThat(request.getBody()).isInstanceOf(HttpRequest.StringRequestBody.class);
        assertThat(request.getBody().getContent()).isEqualTo(content);
    }

    @Test
    void shouldDecodeBodyAsStringWhenContentTypeIsMissing() {
        // When
        HttpRequest request = MicronautHttpService.from(
            io.micronaut.http.HttpRequest.POST(URI, "hello".getBytes(StandardCharsets.UTF_8))
        );

        // Then
        assertThat(request.getBody()).isInstanceOf(HttpRequest.StringRequestBody.class);
        assertThat(request.getBody().getContent()).isEqualTo("hello");
    }

    @Test
    void shouldHaveNoBodyWhenRequestBodyIsEmpty() {
        // When
        HttpRequest request = MicronautHttpService.from(io.micronaut.http.HttpRequest.POST(URI, new byte[0]));

        // Then — an empty body is reported as no body at all, as it was before binary bodies were supported
        assertThat(request.getBody()).isNull();
    }
}
