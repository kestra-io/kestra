package io.kestra.core.http.client;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.URI;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.http.client.configurations.ProxyConfiguration;
import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@KestraTest
@Property(name = "kestra.tasks.http.denied-list", value = "127.0.0.0/8,::1/128")
class HttpClientDeniedAddressTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    private HttpClient client() throws IllegalVariableEvaluationException {
        return HttpClient.builder().runContext(runContextFactory.of()).build();
    }

    @Test
    void shouldDenyHostnameWhenItResolvesToADeniedAddress() throws IllegalVariableEvaluationException, IOException {
        try (HttpClient client = client()) {
            var exception = assertThrows(IllegalArgumentException.class, () -> client.request(
                HttpRequest.of(URI.create("http://localhost:1/")),
                String.class
            ));
            assertThat(exception.getMessage())
                .startsWith("The host 'localhost' resolves to the address '")
                .endsWith("', which is in the configured denied list (kestra.tasks.http.denied-list).");
        }
    }

    @Test
    void shouldDenyHostnameWhenItResolvesToADeniedAddressBehindAProxy() throws IllegalVariableEvaluationException, IOException {
        HttpConfiguration configuration = HttpConfiguration.builder()
            .proxy(
                ProxyConfiguration.builder()
                    .type(io.kestra.core.models.property.Property.ofValue(Proxy.Type.HTTP))
                    .address(io.kestra.core.models.property.Property.ofValue("proxy.example.invalid"))
                    .port(io.kestra.core.models.property.Property.ofValue(3128))
                    .build()
            )
            .build();

        try (HttpClient client = HttpClient.builder().runContext(runContextFactory.of()).configuration(configuration).build()) {
            var exception = assertThrows(IllegalArgumentException.class, () -> client.request(
                HttpRequest.of(URI.create("http://localhost:1/")),
                String.class
            ));
            assertThat(exception.getMessage()).startsWith("The host 'localhost' resolves to the address '");
        }
    }

    @Test
    void shouldDenyIpLiteralWhenItCarriesAnInterfaceZoneId() throws IllegalVariableEvaluationException, IOException {
        assumeTrue(NetworkInterface.getByName("lo") != null, "No loopback interface named lo on this machine");

        try (HttpClient client = client()) {
            var exception = assertThrows(IllegalArgumentException.class, () -> client.request(
                HttpRequest.of(URI.create("http://[::1%lo]:1/")),
                String.class
            ));
            assertThat(exception.getMessage()).isEqualTo("The URI http://[::1%lo]:1/ is in the configured denied list (kestra.tasks.http.denied-list).");
        }
    }
}
