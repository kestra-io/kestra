package io.kestra.core.contexts;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiClientVersionFilterTest implements TestPropertyProvider {
    private static final String ENABLED = "kestra.test.api-client-version-filter";
    private static final String NO_HEADER = "none";

    @Inject
    private ApiClient apiClient;

    @Inject
    private RemoteApiClient remoteApiClient;

    @Inject
    private VersionProvider versionProvider;

    // micronaut.http.services binds through @EachProperty, so the url has to be known before the
    // context starts — which means picking the port the embedded server will bind to.
    @Override
    public @NonNull Map<String, String> getProperties() {
        int port = freePort();
        String url = "http://localhost:" + port;

        return Map.of(
            ENABLED, "true",
            "micronaut.server.port", String.valueOf(port),
            "micronaut.http.services.api.url", url,
            "micronaut.http.services.remote-api.url", url
        );
    }

    @Test
    void shouldDeclareTheInstanceVersionOnCallsToKestrasApi() {
        assertThat(apiClient.version()).isEqualTo(versionProvider.getVersion());
    }

    @Test
    void shouldNotDeclareItOnServicesAUserCanRepoint() {
        assertThat(remoteApiClient.version()).isEqualTo(NO_HEADER);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Client("api")
    @Requires(property = ENABLED, value = "true")
    public interface ApiClient {
        @Get("/echo-kestra-version")
        String version();
    }

    @Client("remote-api")
    @Requires(property = ENABLED, value = "true")
    public interface RemoteApiClient {
        @Get("/echo-kestra-version")
        String version();
    }

    @Controller("/echo-kestra-version")
    @Requires(property = ENABLED, value = "true")
    public static class EchoController {
        @Get
        public String version(HttpRequest<?> request) {
            return Optional.ofNullable(request.getHeaders().get(ApiClientVersionFilter.VERSION_HEADER))
                .orElse(NO_HEADER);
        }
    }
}
