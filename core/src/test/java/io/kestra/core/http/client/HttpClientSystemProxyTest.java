package io.kestra.core.http.client;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "kestra.tasks.http.use-system-proxy", value = "true")
@Property(name = "kestra.tasks.http.denied-list", value = "127.0.0.0/8,::1/128")
class HttpClientSystemProxyTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    private final AtomicInteger challenges = new AtomicInteger();
    private HttpServer proxy;
    private Properties previousProperties;

    @BeforeEach
    void startProxy() throws Exception {
        String credentials = "Basic " + Base64.getEncoder().encodeToString("kestra:s3cret".getBytes(StandardCharsets.UTF_8));
        proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            if (!credentials.equals(exchange.getRequestHeaders().getFirst("Proxy-Authorization"))) {
                challenges.incrementAndGet();
                exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"proxy\"");
                exchange.sendResponseHeaders(407, -1);
                exchange.close();
                return;
            }
            byte[] body = "via-proxy".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        proxy.start();

        previousProperties = (Properties) System.getProperties().clone();
        System.setProperty("http.proxyHost", "localhost");
        System.setProperty("http.proxyPort", String.valueOf(proxy.getAddress().getPort()));
        System.setProperty("http.proxyUser", "kestra");
        System.setProperty("http.proxyPassword", "s3cret");
        System.setProperty("http.nonProxyHosts", "");
    }

    @AfterEach
    void stopProxy() {
        proxy.stop(0);
        System.setProperties(previousProperties);
    }

    @Test
    void shouldGoThroughJvmProxyInADeniedRangeWithItsCredentialsWhenTaskSetsNoProxy() throws Exception {
        try (HttpClient client = HttpClient.builder().runContext(runContextFactory.of()).build()) {
            var response = client.request(HttpRequest.of(URI.create("http://external.example.invalid/")), String.class);

            assertThat(response.getBody()).isEqualTo("via-proxy");
            assertThat(challenges).hasPositiveValue();
        }
    }
}
