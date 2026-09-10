package io.kestra.core.http.client;

import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@Property(name = "kestra.tasks.http.allowed-list", value = "http://localhost")
class HttpClientAllowedListTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    private HttpClient client() throws IllegalVariableEvaluationException {
        return HttpClient.builder().runContext(runContextFactory.of()).build();
    }

    @Test
    void shouldRejectUriWhenHostIsDisguisedViaUserInfo() throws IllegalVariableEvaluationException, IOException {
        try (HttpClient client = client()) {
            var exception = assertThrows(IllegalArgumentException.class, () -> client.request(
                HttpRequest.of(URI.create("http://localhost@169.254.169.254/")),
                String.class
            ));
            assertThat(exception.getMessage()).isEqualTo("The URI http://localhost@169.254.169.254/ is not in the configured allowed list (kestra.tasks.http.allowed-list).");
        }
    }

    @Test
    void shouldRejectUriWhenHostIsDisguisedViaSubdomainSuffix() throws IllegalVariableEvaluationException, IOException {
        try (HttpClient client = client()) {
            var exception = assertThrows(IllegalArgumentException.class, () -> client.request(
                HttpRequest.of(URI.create("http://localhost.attacker.example/")),
                String.class
            ));
            assertThat(exception.getMessage()).isEqualTo("The URI http://localhost.attacker.example/ is not in the configured allowed list (kestra.tasks.http.allowed-list).");
        }
    }

    @Test
    void shouldAllowRequestWhenHostExactlyMatchesAllowedEntry() throws IllegalVariableEvaluationException, IOException {
        try (HttpClient client = client()) {
            // localhost matches the allowed list, so the request must fail on connecting to the
            // (almost certainly unbound) port rather than on the allow-list check itself.
            assertThrows(HttpClientRequestException.class, () -> client.request(
                HttpRequest.of(URI.create("http://localhost:1/")),
                String.class
            ));
        }
    }
}
