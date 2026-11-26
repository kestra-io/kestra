package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class StaticFilterTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void shouldServeRewrittenIndexForUnknownUiPath() {
        // When
        HttpResponse<String> response = client.toBlocking().exchange(GET("/ui/assets/does-not-exist.js"), String.class);

        // Then
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.getContentType()).contains(MediaType.TEXT_HTML_TYPE);
        assertThat(response.body())
            .contains("window.KESTRA_UI_PATH = \"/ui/\";")
            .doesNotContain("<meta name=\"html-head\" content=\"replace\">");
    }

    @Test
    void shouldServeSameIndexUnderConcurrentRequests() throws Exception {
        // Given
        String expected = client.toBlocking().retrieve(GET("/ui/"));
        List<Callable<String>> requests = IntStream.range(0, 200)
            .<Callable<String>> mapToObj(i -> () -> client.toBlocking().retrieve(GET("/ui/assets/probe-" + i + ".js")))
            .toList();

        // When
        List<Future<String>> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(20)) {
            results = executor.invokeAll(requests);
        }

        // Then
        for (Future<String> result : results) {
            assertThat(result.get()).isEqualTo(expected);
        }
    }
}
