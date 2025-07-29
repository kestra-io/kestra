package io.kestra.webserver.controllers.api;

import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_ERROR_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.webserver.controllers.api.MiscController.BasicAuthCredentials;
import io.kestra.webserver.services.BasicAuthService;
import io.kestra.webserver.services.BasicAuthService.BasicAuthConfiguration;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@KestraTest
@Property(name = "kestra.system-flows.namespace", value = "some.system.ns")
class MiscControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    BasicAuthService basicAuthService;

    @Inject
    BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private SettingRepositoryInterface settingRepository;

    @Test
    void ping() {
        var response = client.toBlocking().retrieve("/ping", String.class);

        assertThat(response).isEqualTo("pong");
    }

    @Test
    void getConfiguration() {
        var response = client.toBlocking().retrieve("/api/v1/configs", MiscController.Configuration.class);

        assertThat(response).isNotNull();
        assertThat(response.getUuid()).isNotNull();
        assertThat(response.getIsTaskRunEnabled()).isFalse();
        assertThat(response.getIsAnonymousUsageEnabled()).isTrue();
        assertThat(response.getIsAiEnabled()).isFalse();
        assertThat(response.getSystemNamespace()).isEqualTo("some.system.ns");
    }

    @Test
    void getEmptyValidationErrors() {
        List<String> response = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/basicAuthValidationErrors"), Argument.LIST_OF_STRING);

        assertThat(response).isNotNull();
    }

    @Test
    void getValidationErrors() {
        settingRepository.save(Setting.builder().key(BASIC_AUTH_ERROR_CONFIG).value(List.of("error1", "error2")).build());
        try {
            List<String> response = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/basicAuthValidationErrors"), Argument.LIST_OF_STRING);

            assertThat(response).containsExactly("error1", "error2");
        } finally {
            if (settingRepository.findByKey(BASIC_AUTH_ERROR_CONFIG).isPresent()){
                settingRepository.delete(Setting.builder().key(BASIC_AUTH_ERROR_CONFIG).build());
            }
        }
    }

    @Test
    void saveInvalidBasicAuthConfig(){
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth",
                new BasicAuthCredentials("uid", "invalid", "invalid"))));
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(e.getResponse().getBody(JsonError.class)).isPresent();
        JsonError jsonError = e.getResponse().getBody(JsonError.class).get();
        assertThat(jsonError.getMessage()).isEqualTo("Invalid username for Basic Authentication. Please provide a valid email address., Invalid password for Basic Authentication. The password must have 8 chars, one upper, one lower and one number: Resource fails validation");
    }

    @Test
    void basicAuth() {
        Assertions.assertDoesNotThrow(() -> client.toBlocking().retrieve("/api/v1/configs", MiscController.Configuration.class));

        String uid = "someUid";
        String username = "my.email@kestra.io";
        String password = "myPassword1";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth", new MiscController.BasicAuthCredentials(uid, username, password)));
        try {
            assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().retrieve("/api/v1/main/dashboards", MiscController.Configuration.class)
            );
            assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(
                    HttpRequest.GET("/api/v1/main/dashboards")
                        .basicAuth("bad.user@kestra.io", "badPassword"),
                    MiscController.Configuration.class
                )
            );
            Assertions.assertDoesNotThrow(() -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/main/dashboards")
                    .basicAuth(username, password),
                MiscController.Configuration.class)
            );
        } finally {
            basicAuthService.save(basicAuthConfiguration);
        }
    }
}
