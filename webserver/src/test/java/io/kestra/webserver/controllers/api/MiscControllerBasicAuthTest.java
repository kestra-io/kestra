package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.assertions.Problems;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.errors.ProblemError;
import io.kestra.webserver.errors.ProblemTypes;
import io.kestra.webserver.filter.TestAuthFilter;
import io.kestra.webserver.services.BasicAuthCredentials;
import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_SETTINGS_KEY;
import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Credentials set in the configuration file cannot be changed through the API, so the endpoint is
 * exercised here with them blanked out, the way an instance set up from the UI runs.
 */
@KestraTest
@Property(name = "kestra.server.basic-auth.username", value = "")
@Property(name = "kestra.server.basic-auth.password", value = "")
class MiscControllerBasicAuthTest {
    private static final String USERNAME = "setup.admin@kestra.io";
    private static final String PASSWORD = "SetupPassword1";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    BasicAuthService basicAuthService;

    @Inject
    private SettingRepositoryInterface settingRepository;

    @BeforeEach
    void initializeBasicAuth() {
        basicAuthService.save(new BasicAuthCredentials(null, USERNAME, PASSWORD));
    }

    // Other test contexts share this database and only re-seed their configured credentials when none are stored.
    @AfterEach
    void removeBasicAuth() {
        if (settingRepository.findByKey(BASIC_AUTH_SETTINGS_KEY).isPresent()) {
            settingRepository.delete(Setting.builder().key(BASIC_AUTH_SETTINGS_KEY).build());
        }
    }

    @Test
    void getConfiguration_shouldReportBasicAuthNotManagedByConfig() {
        var response = client.toBlocking().retrieve(GET("/api/v1/configs").basicAuth(USERNAME, PASSWORD), MiscController.Configuration.class);

        assertThat(response.getIsBasicAuthManagedByConfig()).isFalse();
    }

    @Test
    void saveInvalidBasicAuthConfig() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest.POST(
                    "/api/v1/main/basicAuth",
                    new BasicAuthCredentials("uid", "invalid", "invalid", PASSWORD)
                ).basicAuth(USERNAME, PASSWORD)
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

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, "WrongCurrentPassword1"))
                    .basicAuth(USERNAME, PASSWORD)
            )
        );
        Problems.assertProblem(e, ProblemTypes.VALIDATION_FAILED);

        // the rejected attempt must not have changed anything
        assertThatCode(
            () -> client.toBlocking().retrieve(
                GET("/api/v1/main/dashboards").basicAuth(USERNAME, PASSWORD),
                MiscController.Configuration.class
            )
        ).as("original credentials must still work after a rejected change").doesNotThrowAnyException();

        // the correct current password is accepted
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, PASSWORD))
                .basicAuth(USERNAME, PASSWORD)
        );
        assertThatCode(
            () -> client.toBlocking().retrieve(
                GET("/api/v1/main/dashboards").basicAuth(username, password),
                MiscController.Configuration.class
            )
        ).as("new credentials must work after a change with the correct current password").doesNotThrowAnyException();
    }

    @Test
    void changeBasicAuth_shouldNotRequireCurrentPassword_beforeInitialization() {
        // TestAuthFilter transparently re-initializes Basic Authentication before every outgoing
        // test request whenever credentials are absent, which would silently undo the delete
        // below before the request even reaches the server; disable it to genuinely exercise
        // the not-yet-initialized path.
        TestAuthFilter.ENABLED = false;
        try {
            settingRepository.delete(Setting.builder().key(BASIC_AUTH_SETTINGS_KEY).build());
            assertThat(basicAuthService.isBasicAuthInitialized()).isFalse();

            assertThatCode(
                () -> client.toBlocking().exchange(
                    HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials("initUid", "first.setup@kestra.io", "FirstSetupPassword1"))
                )
            ).as("initial setup must not require a current password").doesNotThrowAnyException();

            assertThat(basicAuthService.isBasicAuthInitialized()).isTrue();
        } finally {
            TestAuthFilter.ENABLED = true;
        }
    }

    @FlakyTest(description = "BasicAuth state from other tests leaks; needs full security lifecycle isolation")
    @Test
    void basicAuth() {
        assertThatCode(
            () -> client.toBlocking().retrieve(GET("/api/v1/configs").basicAuth(USERNAME, PASSWORD), MiscController.Configuration.class)
        ).doesNotThrowAnyException();

        String uid = "someUid";
        String username = "my.email@kestra.io";
        String password = "myPassword1";
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/basicAuth", new BasicAuthCredentials(uid, username, password, PASSWORD)).basicAuth(USERNAME, PASSWORD)
        );

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
    }

    @Test
    void basicAuthShouldBeOpenBeforeSetupOnly() {
        TestAuthFilter.ENABLED = false;
        try {
            HttpClientResponseException httpClientResponseException = assertThrows(
                HttpClientResponseException.class, () -> client.toBlocking()
                    .exchange(HttpRequest.GET("/api/v1/basicAuthValidationErrors"))
            );
            assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

            httpClientResponseException = assertThrows(
                HttpClientResponseException.class, () -> client.toBlocking()
                    .exchange(HttpRequest.POST("/api/v1/basicAuth", new BasicAuthCredentials(IdUtils.create(), "anonymous", "hacker")))
            );
            assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

            HttpResponse<?> response = client.toBlocking()
                .exchange(
                    HttpRequest.POST(
                        "/api/v1/basicAuth", new BasicAuthCredentials(IdUtils.create(), "anonymous@hacker", "hackerPassword1", PASSWORD)
                    ).basicAuth(USERNAME, PASSWORD)
                );
            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());

            response = client.toBlocking()
                .exchange(HttpRequest.GET("/api/v1/basicAuthValidationErrors").basicAuth("anonymous@hacker", "hackerPassword1"));
            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

            // Only 1 basic auth user is allowed so the previous one is overridden
            httpClientResponseException = assertThrows(
                HttpClientResponseException.class, () -> client.toBlocking()
                    .exchange(HttpRequest.GET("/api/v1/basicAuthValidationErrors").basicAuth(USERNAME, PASSWORD))
            );
            assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());

            assertThat(basicAuthService.isBasicAuthInitialized()).isTrue();
            settingRepository.delete(Setting.builder().key(BASIC_AUTH_SETTINGS_KEY).build());
            assertThat(basicAuthService.isBasicAuthInitialized()).isFalse();

            response = client.toBlocking()
                .exchange(HttpRequest.GET("/api/v1/basicAuthValidationErrors"));
            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

            response = client.toBlocking()
                .exchange(HttpRequest.POST("/api/v1/basicAuth", new BasicAuthCredentials(IdUtils.create(), USERNAME, PASSWORD)));
            assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());

            assertThat(basicAuthService.isBasicAuthInitialized()).isTrue();
        } finally {
            TestAuthFilter.ENABLED = true;
        }
    }
}
