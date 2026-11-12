package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "micronaut.server.context-path", value = "/kestra")
class ApiControllerTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void shouldLoadRapidocAsModuleFromUiBuildWhenRenderingApiPage() {
        String html = client.toBlocking().retrieve(HttpRequest.GET("/kestra/api"));

        assertThat(html)
            .contains("<script type=\"module\" src=\"/kestra/ui/vendor/rapidoc-min.js\"></script>")
            .doesNotContain("unpkg.com");
    }
}
