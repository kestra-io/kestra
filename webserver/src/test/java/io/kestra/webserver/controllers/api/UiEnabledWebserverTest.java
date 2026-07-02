package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class UiEnabledWebserverTest {
    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    ApplicationContext applicationContext;

    @Test
    void shouldRedirectRootToUiByDefault() {
        // Given - a client that does not follow redirects
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setFollowRedirects(false);

        try (HttpClient client = HttpClient.create(embeddedServer.getURL(), configuration)) {
            // When
            HttpResponse<?> response = client.toBlocking().exchange(GET("/"));

            // Then
            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT.getCode());
            assertThat(response.header("Location")).isEqualTo("/ui/");
        }
    }

    @Test
    void shouldRegisterUiBeansByDefault() {
        // Then
        assertThat(applicationContext.containsBean(RedirectController.class)).isTrue();
        assertThat(applicationContext.containsBean(StaticFilter.class)).isTrue();
    }
}
