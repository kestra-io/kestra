package io.kestra.webserver.endpoints;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.controllers.domain.ServerInfo;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class VersionEndpointTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private VersionEndpoint versionEndpoint;

    @Inject
    private VersionProvider versionProvider;

    @Test
    void shouldReturnOssVersionAndServerTypeWhenRead() {
        ServerInfo serverInfo = Mono.from(versionEndpoint.version()).block();

        assertThat(serverInfo).isNotNull();
        assertThat(serverInfo.version()).isEqualTo(versionProvider.getVersion() + "-oss");
        assertThat(serverInfo.type()).isEqualTo("STANDALONE");
    }

    @Test
    void shouldServeServerInfoWhenGettingVersionPath() {
        HttpResponse<ServerInfo> response = client.toBlocking()
            .exchange(HttpRequest.GET("/" + VersionEndpoint.NAME), ServerInfo.class);

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.body()).isNotNull();
        assertThat(response.body().version()).isEqualTo(versionProvider.getVersion() + "-oss");
        assertThat(response.body().type()).isEqualTo("STANDALONE");
    }
}
