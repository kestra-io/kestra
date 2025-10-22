package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.models.api.secret.ApiSecretListResponse;
import io.kestra.webserver.models.api.secret.ApiSecretMeta;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class SecretControllerTest {

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void listSecrets() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.total()).isEqualTo(4L);
        assertThat(response.results()).hasSize(4);
        assertThat(response.results().stream().map(ApiSecretMeta::getKey).toList())
            .containsExactlyInAnyOrder("WEBHOOK_KEY", "PASSWORD", "MY_SECRET", "NEW_LINE");
    }

    @Test
    void listSecretsWithPagination() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?page=1&size=2"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.total()).isEqualTo(4L);
        assertThat(response.results()).hasSize(2);
    }

    @Test
    void listSecretsWithPaginationSecondPage() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?page=2&size=2"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.total()).isEqualTo(4L);
        assertThat(response.results()).hasSize(2);
    }

    @Test
    void listSecretsWithQuery() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?filters[q][EQUALS]=key"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.results()).isNotEmpty();
        assertThat(response.results().stream().map(ApiSecretMeta::getKey).toList())
            .anyMatch(key -> key.toLowerCase().contains("key"));
    }

    @Test
    void listSecretsWithQueryPassword() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?filters[q][EQUALS]=password"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).getKey()).isEqualTo("PASSWORD");
    }

    @Test
    void listSecretsWithQueryCaseInsensitive() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?filters[q][EQUALS]=WEBHOOK"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.results()).isNotEmpty();
        assertThat(response.results().stream().map(ApiSecretMeta::getKey).toList())
            .contains("WEBHOOK_KEY");
    }

    @Test
    void listSecretsWithSort() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?sort=key:asc"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.total()).isEqualTo(4L);
        assertThat(response.results()).hasSize(4);
    }

    @Test
    void listSecretsWithCustomPageSize() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?page=1&size=3"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.total()).isEqualTo(4L);
        assertThat(response.results()).hasSize(3);
    }

    @Test
    void listSecretsDefaultParameters() {
        // Test with default parameters (page=1, size=10)
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.results()).isNotNull();
        assertThat(response.results()).hasSize(4);
    }

    @Test
    void listSecretsEmptyQuery() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?filters[q][EQUALS]="),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.results()).isNotNull();
        assertThat(response.total()).isEqualTo(4L);
    }

    @Test
    void listSecretsWithNoMatch() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?filters[q][EQUALS]=nonexistent"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.total()).isEqualTo(0L);
        assertThat(response.results()).isEmpty();
    }

    @Test
    void listSecretsLastPage() {
        ApiSecretListResponse<ApiSecretMeta> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/secrets?page=2&size=3"),
            ApiSecretListResponse.class
        );

        assertThat(response.readOnly()).isTrue();
        assertThat(response.total()).isEqualTo(4L);
        assertThat(response.results()).hasSize(1);
    }
}
