package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Setting;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.BasicAuthCredentials;
import io.kestra.webserver.services.BasicAuthService;
import io.kestra.webserver.services.BasicAuthService.BasicAuthConfiguration;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_ERROR_CONFIG;
import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Inject
    private FlowRepositoryInterface flowRepository;

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
        assertThat(response.getIsAnonymousUsageEnabled()).isTrue();
        assertThat(response.getIsAiEnabled()).isTrue();
        assertThat(response.getSystemNamespace()).isEqualTo("some.system.ns");
        assertThat(response.getIsConcurrencyViewEnabled()).isTrue();
    }

    @Test
    void getEmptyValidationErrors() {
        List<String> response = client.toBlocking().retrieve(GET("/api/v1/basicAuthValidationErrors"), Argument.LIST_OF_STRING);

        assertThat(response).isNotNull();
    }

    @Test
    void getValidationErrors() {
        settingRepository.save(Setting.builder().key(BASIC_AUTH_ERROR_CONFIG).value(List.of("error1", "error2")).build());
        try {
            List<String> response = client.toBlocking().retrieve(GET("/api/v1/basicAuthValidationErrors"), Argument.LIST_OF_STRING);

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
        assertThatCode(() -> client.toBlocking().retrieve("/api/v1/configs", MiscController.Configuration.class)).doesNotThrowAnyException();

        String uid = "someUid";
        String username = "my.email@kestra.io";
        String password = "myPassword1";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password)));
        try {
            assertThatThrownBy(
                () -> client.toBlocking().retrieve("/api/v1/main/dashboards", MiscController.Configuration.class)
            )
                .as("expect 401 for unauthenticated GET /api/v1/main/dashboards")
                .isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
                    assertThat((CharSequence) ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
                );

            assertThatThrownBy(
                () -> client.toBlocking().retrieve(
                    GET("/api/v1/main/dashboards")
                        .basicAuth("bad.user@kestra.io", "badPassword"),
                    MiscController.Configuration.class
                )
            ).as("expect 401 for GET /api/v1/main/dashboards with wrong password")
                .isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
                    assertThat((CharSequence) ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
                );

            assertThatCode(() -> client.toBlocking().retrieve(
                GET("/api/v1/main/dashboards")
                    .basicAuth(username, password),
                MiscController.Configuration.class)
            ).as("expect success GET /api/v1/main/dashboards with good password")
                .doesNotThrowAnyException();
        } finally {
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }

    @Test
    void canTriggerAWebhookWithoutBasicAuth() {
        String uid = "someUid2";
        String username = "my.email2@kestra.io";
        String password = "myPassword2";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password)));

        try {
            var namespace = "namespace1";
            var flowId = "flowWithWebhook" + IdUtils.create();
            var key = "1KERKzRQZSMtLdMdNI7Nkr";
            var flowWithWebhook = """
                id: %s
                namespace: %s
                tasks:
                  - id: out
                    type: io.kestra.plugin.core.debug.Return
                    format: "output1"
                triggers:
                  - id: webhook_trigger
                    type: io.kestra.plugin.core.trigger.Webhook
                    key: %s
                disabled: false
                deleted: false
                """.formatted(flowId, namespace, key);

            assertThatCode(() -> client.toBlocking().retrieve(
                POST("/api/v1/main/flows", flowWithWebhook)
                    .contentType(MediaType.APPLICATION_YAML)
                    .basicAuth(username, password),
                FlowWithSource.class)
            ).as("can create a Flow with webhook when authenticated")
                .doesNotThrowAnyException();

            assertThatCode(() -> client.toBlocking().retrieve(POST("/api/v1/main/executions/webhook/{namespace}/{flowId}/{key}"
                    .replace("{namespace}", namespace)
                    .replace("{flowId}", flowId)
                    .replace("{key}", key)
                , flowWithWebhook), FlowWithSource.class)
            ).as("can trigger this Flow webhook when not authenticated")
                .doesNotThrowAnyException();
        } finally {
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }
}
