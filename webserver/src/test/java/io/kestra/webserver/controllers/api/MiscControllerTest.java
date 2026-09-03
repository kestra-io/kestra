package io.kestra.webserver.controllers.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Setting;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.runners.pebble.PebbleFunction;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.filter.TestAuthFilter;
import io.kestra.webserver.services.BasicAuthCredentials;
import io.kestra.webserver.services.BasicAuthService;
import io.kestra.webserver.services.BasicAuthService.BasicAuthConfiguration;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.kestra.core.junit.assertions.Problems;
import io.kestra.webserver.errors.ProblemTypes;
import io.kestra.webserver.errors.ProblemError;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_ERROR_CONFIG;
import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@Property(name = "kestra.system-flows.namespace", value = "some.system.ns")
@Property(name = "kestra.flowTemplate", value = "tasks:\n  - id: configured\n    type: io.kestra.plugin.core.log.Log\n    message: Configured")
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
    void getExpressionFilters() {
        List<String> response = client.toBlocking().retrieve(GET("/api/v1/pebble/filters"), Argument.LIST_OF_STRING);

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        // Kestra custom filters
        assertThat(response).contains("jq", "toJson", "yaml", "slugify", "chunk", "flatten");
        // Pebble core filters
        assertThat(response).contains("capitalize", "upper", "lower", "trim", "first", "last");
        // Should be sorted
        assertThat(response).isSorted();
    }

    @Test
    void getExpressionFunctions() {
        List<PebbleFunction> response = client.toBlocking().retrieve(GET("/api/v1/pebble/functions"), Argument.listOf(PebbleFunction.class));

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        // Kestra custom functions
        assertThat(response).extracting(PebbleFunction::name).contains("now", "secret", "kv", "uuid", "yaml");
        // Pebble core functions
        assertThat(response).extracting(PebbleFunction::name).contains("max", "min", "range");
        // Should be sorted
        assertThat(response).extracting(PebbleFunction::name).isSorted();
    }

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
        assertThat(response.getFlowTemplate()).isEqualTo("tasks:\n  - id: configured\n    type: io.kestra.plugin.core.log.Log\n    message: Configured");
        assertThat(response.getIsAiApiKeyConfigured()).isNotNull();
    }

    @Test
    void getLoginConfiguration() {
        // /api/v1/configs/login is the only config endpoint reachable without authentication:
        // it must expose isBasicAuthInitialized and nothing else (no version, commit id, queue
        // type, plugin hash, system namespace, url, ...).
        TestAuthFilter.ENABLED = false;
        try {
            var response = client.toBlocking().retrieve(GET("/api/v1/configs/login"), Argument.mapOf(String.class, Object.class));

            assertThat(response).containsOnlyKeys("isBasicAuthInitialized");
            assertThat(response.get("isBasicAuthInitialized")).isEqualTo(true);
        } finally {
            TestAuthFilter.ENABLED = true;
        }
    }

    @Test
    @FlakyTest(description = "BasicAuth state from other tests leaks; needs full security lifecycle isolation")
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
            if (settingRepository.findByKey(BASIC_AUTH_ERROR_CONFIG).isPresent()) {
                settingRepository.delete(Setting.builder().key(BASIC_AUTH_ERROR_CONFIG).build());
            }
        }
    }

    @Test
    void saveInvalidBasicAuthConfig() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest.POST(
                    "/api/v1/main/basicAuth",
                    new BasicAuthCredentials("uid", "invalid", "invalid", basicAuthConfiguration.getPassword())
                )
            )
        );
        // Each rejected rule is now a separate errors[] entry instead of one comma-joined string.
        Problems.assertProblem(e, ProblemTypes.VALIDATION_FAILED);
        Problems.assertErrors(e)
            .extracting(ProblemError::detail)
            .containsExactlyInAnyOrder(
                "Invalid username for Basic Authentication. Please provide a valid email address.",
                "Invalid password for Basic Authentication. The password must have 8 chars, one upper, one lower and one number"
            );
    }

    @FlakyTest(description = "BasicAuth state from other tests leaks; needs full security lifecycle isolation")
    @Test
    void changeBasicAuth_shouldRejectWrongCurrentPassword_whenAlreadyInitialized() {
        // GHSA-94pv-f379-3gp3: changing Basic Authentication credentials must re-check the
        // current password directly against the stored value, not rely on isAuthenticated()
        // alone, which can be satisfied by a token cached before a peer node's password rotation.
        String uid = "requireCurrentPasswordUid";
        String username = "require.current.password@kestra.io";
        String password = "newSecurePassword1";

        try {
            HttpClientResponseException e = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                    HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, "WrongCurrentPassword1"))
                )
            );
            Problems.assertProblem(e, ProblemTypes.VALIDATION_FAILED);

            // the rejected attempt must not have changed anything
            assertThatCode(
                () -> client.toBlocking().retrieve(
                    GET("/api/v1/main/dashboards").basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()),
                    MiscController.Configuration.class
                )
            ).as("original credentials must still work after a rejected change").doesNotThrowAnyException();

            // the correct current password is accepted
            client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, basicAuthConfiguration.getPassword()))
            );
            assertThatCode(
                () -> client.toBlocking().retrieve(
                    GET("/api/v1/main/dashboards").basicAuth(username, password),
                    MiscController.Configuration.class
                )
            ).as("new credentials must work after a change with the correct current password").doesNotThrowAnyException();
        } finally {
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }

    @Test
    void changeBasicAuth_shouldNotRequireCurrentPassword_beforeInitialization() {
        // TestAuthFilter transparently re-initializes Basic Authentication before every outgoing
        // test request whenever credentials are absent, which would silently undo the delete
        // below before the request even reaches the server; disable it to genuinely exercise
        // the not-yet-initialized path.
        TestAuthFilter.ENABLED = false;
        try {
            settingRepository.delete(Setting.builder().key(BasicAuthService.BASIC_AUTH_SETTINGS_KEY).build());
            assertThat(basicAuthService.isBasicAuthInitialized()).isFalse();

            assertThatCode(
                () -> client.toBlocking().exchange(
                    HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials("initUid", "first.setup@kestra.io", "FirstSetupPassword1"))
                )
            ).as("initial setup must not require a current password").doesNotThrowAnyException();

            assertThat(basicAuthService.isBasicAuthInitialized()).isTrue();
        } finally {
            TestAuthFilter.ENABLED = true;
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }

    @FlakyTest(description = "BasicAuth state from other tests leaks; needs full security lifecycle isolation")
    @Test
    void basicAuth() {
        assertThatCode(() -> client.toBlocking().retrieve("/api/v1/configs", MiscController.Configuration.class)).doesNotThrowAnyException();

        String uid = "someUid";
        String username = "my.email@kestra.io";
        String password = "myPassword1";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, basicAuthConfiguration.getPassword())));
        try {
            assertThatThrownBy(
                () -> client.toBlocking().retrieve("/api/v1/main/dashboards", MiscController.Configuration.class)
            )
                .as("expect 401 for unauthenticated GET /api/v1/main/dashboards")
                .isInstanceOfSatisfying(
                    HttpClientResponseException.class, ex -> assertThat((CharSequence) ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
                );

            assertThatThrownBy(
                () -> client.toBlocking().retrieve(
                    GET("/api/v1/main/dashboards")
                        .basicAuth("bad.user@kestra.io", "badPassword"),
                    MiscController.Configuration.class
                )
            ).as("expect 401 for GET /api/v1/main/dashboards with wrong password")
                .isInstanceOfSatisfying(
                    HttpClientResponseException.class, ex -> assertThat((CharSequence) ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
                );

            assertThatCode(
                () -> client.toBlocking().retrieve(
                    GET("/api/v1/main/dashboards")
                        .basicAuth(username, password),
                    MiscController.Configuration.class
                )
            ).as("expect success GET /api/v1/main/dashboards with good password")
                .doesNotThrowAnyException();
        } finally {
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }

    @FlakyTest(description = "BasicAuth state from other tests leaks; needs full security lifecycle isolation")
    @Test
    void login_shouldSetHttpOnlyCookie_withValidCredentials() {
        String uid = "loginUid";
        String username = "login.success@kestra.io";
        String password = "loginPassword1";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, basicAuthConfiguration.getPassword())));

        try {
            var response = client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/login", new MiscController.LoginRequest(username, password))
            );

            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
            var cookie = response.getCookie(BasicAuthService.BASIC_AUTH_COOKIE_NAME);
            assertThat(cookie).isPresent();
            assertThat(cookie.get().isHttpOnly()).isTrue();
        } finally {
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }

    @FlakyTest(description = "BasicAuth state from other tests leaks; needs full security lifecycle isolation")
    @Test
    void login_shouldSetNonHttpOnlyFlagCookie_withValidCredentials() {
        String uid = "loginFlagUid";
        String username = "login.flag.success@kestra.io";
        String password = "loginPassword1";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, basicAuthConfiguration.getPassword())));

        try {
            var response = client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/login", new MiscController.LoginRequest(username, password))
            );

            var flagCookie = response.getCookie(BasicAuthService.BASIC_AUTH_FLAG_COOKIE_NAME);
            assertThat(flagCookie).isPresent();
            assertThat(flagCookie.get().isHttpOnly()).isFalse();
            assertThat(flagCookie.get().getValue()).isEqualTo("true");
        } finally {
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }

    @FlakyTest(description = "BasicAuth state from other tests leaks; needs full security lifecycle isolation")
    @Test
    void login_shouldReject_withInvalidCredentials() {
        String uid = "loginUid2";
        String username = "login.fail@kestra.io";
        String password = "loginPassword2";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, basicAuthConfiguration.getPassword())));

        try {
            assertThatThrownBy(
                () -> client.toBlocking().exchange(
                    HttpRequest.POST("/api/v1/login", new MiscController.LoginRequest(username, "wrongPassword"))
                )
            ).isInstanceOfSatisfying(
                HttpClientResponseException.class, ex -> assertThat((CharSequence) ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        } finally {
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }

    @Test
    void login_isReachableWithoutPriorAuthentication() {
        // /api/v1/login must be reachable by an unauthenticated caller, otherwise nobody could ever log in
        assertThatThrownBy(
            () -> client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/login", new MiscController.LoginRequest("nobody@kestra.io", "wrongPassword"))
            )
        ).isInstanceOfSatisfying(
            // rejected for bad credentials, not for missing authentication
            HttpClientResponseException.class, ex -> assertThat((CharSequence) ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
        );
    }

    @FlakyTest(description = "BasicAuth state from other tests leaks; needs full security lifecycle isolation")
    @Test
    void logout_shouldClearCookie_whenAuthenticated() {
        String uid = "logoutUid";
        String username = "logout.success@kestra.io";
        String password = "logoutPassword1";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, basicAuthConfiguration.getPassword())));

        try {
            var response = client.toBlocking().exchange(POST("/api/v1/logout", null).basicAuth(username, password));

            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
            var cookie = response.getCookie(BasicAuthService.BASIC_AUTH_COOKIE_NAME);
            assertThat(cookie).isPresent();
            assertThat(cookie.get().getMaxAge()).isEqualTo(0);

            var flagCookie = response.getCookie(BasicAuthService.BASIC_AUTH_FLAG_COOKIE_NAME);
            assertThat(flagCookie).isPresent();
            assertThat(flagCookie.get().getMaxAge()).isEqualTo(0);
        } finally {
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }

    @Test
    void logout_shouldRequireAuthentication() {
        // unlike /login, /logout must not bypass AuthenticationFilter: an unauthenticated caller has
        // no session to clear, so the request is rejected rather than silently accepted.
        // TestAuthFilter transparently attaches a valid Authorization header to every outgoing test
        // request unless disabled, so it must be turned off to genuinely exercise the unauthenticated path.
        TestAuthFilter.ENABLED = false;
        try {
            assertThatThrownBy(
                () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/logout", null))
            ).isInstanceOfSatisfying(
                HttpClientResponseException.class, ex -> assertThat((CharSequence) ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        } finally {
            TestAuthFilter.ENABLED = true;
        }
    }

    @Test
    void canTriggerAWebhookWithoutBasicAuth() {
        String uid = "someUid2";
        String username = "my.email2@kestra.io";
        String password = "myPassword2";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, basicAuthConfiguration.getPassword())));

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

            assertThatCode(
                () -> client.toBlocking().retrieve(
                    POST("/api/v1/main/flows", flowWithWebhook)
                        .contentType(MediaType.APPLICATION_YAML)
                        .basicAuth(username, password),
                    FlowWithSource.class
                )
            ).as("can create a Flow with webhook when authenticated")
                .doesNotThrowAnyException();

            // The test verifies the auth property: webhooks must be reachable without credentials even
            // when basic-auth is globally enabled.  A 401/403 would mean the webhook is incorrectly
            // protected; any other status (200, 409, 500 …) is acceptable here.
            // Capture the HTTP status whether the call succeeds or throws HttpClientResponseException.
            int webhookStatus;
            try {
                webhookStatus = client.toBlocking().exchange(
                    POST(
                        "/api/v1/main/executions/webhook/{namespace}/{flowId}/{key}"
                            .replace("{namespace}", namespace)
                            .replace("{flowId}", flowId)
                            .replace("{key}", key),
                        flowWithWebhook
                    ), String.class
                ).getStatus().getCode();
            } catch (HttpClientResponseException e) {
                webhookStatus = e.getStatus().getCode();
            }
            assertThat(webhookStatus)
                .as("webhook must be reachable without credentials")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED.getCode())
                .isNotEqualTo(HttpStatus.FORBIDDEN.getCode());
        } finally {
            basicAuthService.save(new BasicAuthCredentials(null, basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));
        }
    }
}
