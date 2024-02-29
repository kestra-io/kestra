package io.kestra.core.endpoints;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.MapPropertySource;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.BiConsumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;


class BasicAuthEndpointsFilterTest {
    void test(boolean password, BiConsumer<ReactorHttpClient, MutableHttpRequest<String>> consumer) {
        MapPropertySource mapPropertySource = new MapPropertySource(
            "unittest",
            password ?
                Map.of(
                    "endpoints.all.enabled", true,
                    "endpoints.all.sensitive", false,
                    "endpoints.all.basic-auth.username", "foo",
                    "endpoints.all.basic-auth.password", "bar"
                ) :
                Map.of(
                    "endpoints.all.enabled", true,
                    "endpoints.all.sensitive", false
                )
        );

        try (ApplicationContext ctx = ApplicationContext.run(mapPropertySource, Environment.CLI, Environment.TEST)) {
            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            ReactorHttpClient client = ctx.getBean(ReactorHttpClient.class);

            consumer.accept(client, HttpRequest.GET("http://localhost:" + embeddedServer.getPort() +"/health"));
        }
    }

    @Test
    void withPasswordOk() {
       test(true, (client, httpRequest) -> {
           HttpResponse<String> response = client.toBlocking().exchange(httpRequest.basicAuth("foo", "bar"));
           assertThat(response.getStatus(), is(HttpStatus.OK));
       });
    }

    @Test
    void withPasswordKo() {
        test(true, (client, httpRequest) -> {
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
                client.toBlocking().exchange(httpRequest.basicAuth("foo", "bar2"));
            });

            assertThat(e.getStatus(), is(HttpStatus.UNAUTHORIZED));
        });

        test(true, (client, httpRequest) -> {
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
                client.toBlocking().exchange(httpRequest);
            });

            assertThat(e.getStatus(), is(HttpStatus.UNAUTHORIZED));
        });
    }

    @Test
    void withoutPasswordOk() {
        test(false, (client, httpRequest) -> {
            HttpResponse<String> response = client.toBlocking().exchange(httpRequest);
            assertThat(response.getStatus(), is(HttpStatus.OK));
        });
    }
}