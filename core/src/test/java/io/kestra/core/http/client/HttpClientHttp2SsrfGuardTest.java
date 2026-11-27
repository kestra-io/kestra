package io.kestra.core.http.client;

import java.net.URI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.HttpConfiguration.HttpVersion;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// The server is reached as 127.0.0.2 because 127.0.0.1, which "localhost" resolves to, is denied.
@KestraTest
@io.micronaut.context.annotation.Property(name = "kestra.tasks.http.denied-list", value = "169.254.0.0/16,127.0.0.1/32")
@Timeout(30)
class HttpClientHttp2SsrfGuardTest {
    private static WireMockServer server;

    @Inject
    private TestRunContextFactory runContextFactory;

    @BeforeAll
    static void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort().http2PlainDisabled(false));
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @Test
    void shouldDenyRedirectToDeniedAddressWhenUsingHttp2() throws Exception {
        server.stubFor(get("/metadata").willReturn(temporaryRedirect("http://169.254.169.254/latest/meta-data/")));

        try (HttpClient client = http2Client()) {
            var exception = assertThrows(IllegalArgumentException.class, () -> client.request(HttpRequest.of(URI.create("http://127.0.0.2:" + server.port() + "/metadata")), String.class));

            assertThat(exception.getMessage()).contains("169.254.169.254").contains("is in the configured denied list");
        }
    }

    @Test
    void shouldDenyRedirectToHostResolvingToDeniedAddressWhenUsingHttp2() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"), "127.0.0.2 is only a loopback address on Linux");
        server.stubFor(get("/dns").willReturn(temporaryRedirect("http://localhost:" + server.port() + "/to")));

        try (HttpClient client = http2Client()) {
            var exception = assertThrows(IllegalArgumentException.class, () -> client.request(HttpRequest.of(URI.create("http://127.0.0.2:" + server.port() + "/dns")), String.class));

            assertThat(exception.getMessage()).startsWith("The host 'localhost' resolves to the address '127.0.0.1'");
        }
    }

    private HttpClient http2Client() throws Exception {
        return HttpClient.builder()
            .runContext(runContextFactory.of())
            .configuration(HttpConfiguration.builder().httpVersion(Property.ofValue(HttpVersion.HTTP_2)).build())
            .build();
    }
}
