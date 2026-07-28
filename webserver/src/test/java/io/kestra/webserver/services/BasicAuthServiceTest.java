package io.kestra.webserver.services;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import java.util.Base64;

import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.InMemorySettingRepository;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.AuthUtils;
import io.kestra.webserver.models.events.Event;
import io.kestra.webserver.models.events.OssAuthEvent;
import io.kestra.webserver.services.BasicAuthService.BasicAuthConfiguration;

import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_ERROR_CONFIG;
import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_SETTINGS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest(httpPort = 28181)
@KestraTest(environments = Environment.TEST)
class BasicAuthServiceTest {

    public static final String PASSWORD = "Password123";
    public static final String USER_NAME = "user@kestra.io";

    @Inject
    private BasicAuthConfiguration yamlBasicAuthConfiguration;

    @Inject
    private ApplicationEventPublisher<OssAuthEvent> ossAuthEventPublisher;

    @Inject
    private InstanceService instanceService;

    @BeforeEach
    void setUp() {
        stubFor(
            any(urlMatching(".*"))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withBody("No stub matched")
                )
        );
    }

    @Test
    void yamlConfigurationIsLoaded() {
        assertThat(yamlBasicAuthConfiguration).isNotNull();
        assertThat(yamlBasicAuthConfiguration).extracting(BasicAuthConfiguration::getPassword).isEqualTo("Kestra123");
        assertThat(yamlBasicAuthConfiguration).extracting(BasicAuthConfiguration::getUsername).isEqualTo("admin@kestra.io");
        assertThat(yamlBasicAuthConfiguration).extracting(BasicAuthConfiguration::getOpenUrls).asInstanceOf(LIST)
            .containsExactlyInAnyOrder("/ping", "/api/v1/executions/webhook/", "/api/v1/main/executions/webhook/");
    }

    @Test
    void isBasicAuthInitialized() {
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthConfiguration = new ConfigWrapper(
            new BasicAuthConfiguration(USER_NAME, PASSWORD, null, null)
        ).config;
        var tmpBasicAuthService = new BasicAuthService(tmpSettingsRepo, basicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());

        tmpBasicAuthService.init();
        assertThat(tmpBasicAuthService.isBasicAuthInitialized()).as("isBasicAuthInitialized after init with basic auth configured with user and password").isTrue();

        tmpSettingsRepo.clear();
        assertThat(tmpBasicAuthService.isBasicAuthInitialized()).as("not isBasicAuthInitialized when there is no settings").isFalse();

        tmpBasicAuthService.basicAuthConfiguration = new ConfigWrapper(
            new BasicAuthConfiguration(USER_NAME, null, null, null)
        ).config;
        tmpBasicAuthService.init();
        assertThat(tmpBasicAuthService.isBasicAuthInitialized()).as("not isBasicAuthInitialized when there is settings but only user name").isFalse();

        tmpBasicAuthService.basicAuthConfiguration = new ConfigWrapper(
            new BasicAuthConfiguration(null, null, null, null)
        ).config;
        tmpBasicAuthService.init();
        assertThat(tmpBasicAuthService.isBasicAuthInitialized()).as("not isBasicAuthInitialized when there is settings but no user and password").isFalse();
    }

    @Test
    void basicAuthAPICreation_shouldNot_discardYamlConfiguration() {
        // simulate starting Kestra for the first time
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthConfiguration = new BasicAuthConfiguration(null, null, "Kestra2", List.of("/api/v1/main/executions/webhook/"));
        var tmpBasicAuthService = new BasicAuthService(tmpSettingsRepo, basicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());

        tmpBasicAuthService.init();
        assertFalse(tmpBasicAuthService.isBasicAuthInitialized());

        /**
         * simulate basic auth UI onboarding (createBasicAuth)
         * {@link io.kestra.webserver.controllers.api.MiscController#createBasicAuth(MiscController.BasicAuthCredentials)}
         */
        tmpBasicAuthService.save(
            new BasicAuthCredentials(
                BASIC_AUTH_SETTINGS_KEY,
                "username1@example.com",
                "Password1"
            )
        );
        assertTrue(tmpBasicAuthService.isBasicAuthInitialized());

        assertThat(tmpBasicAuthService.configuration())
            .as("Default configured realm and openUrls should not have been discarded after creating the basic auth user")
            .satisfies(configuration ->
            {
                assertThat(configuration.credentials().getUsername()).isEqualTo("username1@example.com");
                assertThat(configuration.credentials().getPassword()).isNotBlank();
                assertThat(configuration.realm()).isEqualTo("Kestra2");
                assertThat(configuration.openUrls()).isEqualTo(List.of("/api/v1/main/executions/webhook/"));
            });
    }

    @Test
    void basicAuthAPICreation_shouldNot_discardYamlConfiguration_andBeBackwardCompatible_noDefaultCredentials() {
        // simulate starting Kestra for the first time
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthConfiguration = new BasicAuthConfiguration(null, null, "Kestra2", List.of("/api/v1/main/executions/webhook/"));
        var tmpBasicAuthService = new BasicAuthService(tmpSettingsRepo, basicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());

        tmpSettingsRepo.save(
            Setting.builder()
                .key(BASIC_AUTH_SETTINGS_KEY)
                .value(BasicAuthService.SaltedBasicAuthCredentials.salt(null, "username1@example.com", "Password1"))
                .build()
        );
        assertTrue(tmpBasicAuthService.isBasicAuthInitialized());
        tmpBasicAuthService.init();
        assertTrue(tmpBasicAuthService.isBasicAuthInitialized());

        assertThat(tmpBasicAuthService.configuration())
            .as("Default configured realm and openUrls should not have been discarded after creating the basic auth user")
            .satisfies(configuration ->
            {
                assertThat(configuration.credentials().getUsername()).isEqualTo("username1@example.com");
                assertThat(configuration.credentials().getPassword()).isNotBlank();
                assertThat(configuration.realm()).isEqualTo("Kestra2");
                assertThat(configuration.openUrls()).isEqualTo(List.of("/api/v1/main/executions/webhook/"));
            });
    }

    @Test
    void basicAuthAPICreation_shouldNot_discardYamlConfiguration_andBeBackwardCompatible_withDefaultCredentials() {
        // simulate starting Kestra for the first time
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthConfiguration = new BasicAuthConfiguration("username1@example.com", "Password1", "Kestra2", List.of("/api/v1/main/executions/webhook/"));
        var tmpBasicAuthService = new BasicAuthService(tmpSettingsRepo, basicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());

        tmpBasicAuthService.init();
        assertTrue(tmpBasicAuthService.isBasicAuthInitialized());

        assertThat(tmpBasicAuthService.configuration())
            .as("Default configured realm and openUrls should not have been discarded after creating the basic auth user")
            .satisfies(configuration ->
            {
                assertThat(configuration.credentials().getUsername()).isEqualTo("username1@example.com");
                assertThat(configuration.credentials().getPassword()).isNotBlank();
                assertThat(configuration.realm()).isEqualTo("Kestra2");
                assertThat(configuration.openUrls()).isEqualTo(List.of("/api/v1/main/executions/webhook/"));
            });
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class LegacySaltedBasicAuthConfiguration {
        private String salt;
        private String username;
        protected String password;
        private String realm;
        private List<String> openUrls;
    }

    @Test
    void basicAuthAPICreation_shouldStillWork_withLegacyPersistedConfiguration() {
        // given an old configuration containing legacy persisted fields 'realm' and 'openUrls'
        var tmpSettingsRepo = new InMemorySettingRepository();
        var salt = AuthUtils.generateSalt();
        tmpSettingsRepo.save(
            Setting.builder()
                .key(BASIC_AUTH_SETTINGS_KEY)
                .value(
                    new LegacySaltedBasicAuthConfiguration(salt, "username1@example.com", AuthUtils.encodePassword(salt, "Password1"), "OldPersistedRealm", List.of("old-persisted-open-url"))
                )
                .build()
        );
        tmpSettingsRepo.clear();

        var basicAuthConfiguration = new BasicAuthConfiguration("username1@example.com", "Password1", "NewRealmFromConf", List.of("NewOpenurl-fromConf"));
        var tmpBasicAuthService = new BasicAuthService(tmpSettingsRepo, basicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());

        tmpBasicAuthService.init();

        // then
        assertThat(tmpBasicAuthService.configuration())
            .as("should be able to fetch deserialize legacy configuration that contained 'realm' and 'openUrls', we do not persist these fields anymore")
            .satisfies(configuration ->
            {
                assertThat(configuration.credentials().getUsername()).isEqualTo("username1@example.com");
                assertThat(configuration.credentials().getPassword()).startsWith("$2")
                    .as("password must be stored as bcrypt");
                assertThat(configuration.realm()).isEqualTo("NewRealmFromConf");
                assertThat(configuration.openUrls()).isEqualTo(List.of("NewOpenurl-fromConf"));
            });
    }

    @Test
    void shouldMigrateLegacySha512PasswordToBcrypt_atStartup() {
        // Given: a stored SHA-512 credential (as it would exist before the security fix)
        var tmpSettingsRepo = new InMemorySettingRepository();
        String salt = AuthUtils.generateSalt();
        String sha512Hash = AuthUtils.encodePassword(salt, "Kestra123");
        tmpSettingsRepo.save(
            Setting.builder()
                .key(BASIC_AUTH_SETTINGS_KEY)
                .value(new BasicAuthService.SaltedBasicAuthCredentials(salt, "admin@kestra.io", sha512Hash))
                .build()
        );

        // Service with no YAML config — migration must still run
        var tmpBasicAuthService = new BasicAuthService(tmpSettingsRepo, null, instanceService, ApplicationEventPublisher.noOp());

        // When
        tmpBasicAuthService.init();

        // Then: stored hash is now bcrypt
        var credentials = tmpBasicAuthService.configuration().credentials();
        assertThat(credentials.getPassword())
            .as("legacy SHA-512 hash must be migrated to bcrypt at startup")
            .startsWith("$2y$");

        // And: authentication still works with the original plaintext
        assertThat(AuthUtils.matches(credentials.getSalt(), "Kestra123", credentials.getPassword()))
            .as("bcrypt-wrapped hash must still verify against the original password")
            .isTrue();
    }

    @Test
    void shouldStorePasswordAsBcrypt() {
        // Given
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(tmpSettingsRepo, yamlBasicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());

        // When
        basicAuthService.init();

        // Then
        var credentials = basicAuthService.configuration().credentials();
        assertThat(credentials.getPassword())
            .as("stored password must be a bcrypt modular-crypt string")
            .startsWith("$2y$");
    }

    @Test
    void shouldNotReSaveCredentials_whenSamePasswordIsUsedOnRestart() {
        // Given – first startup persists bcrypt credentials
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(tmpSettingsRepo, yamlBasicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());
        basicAuthService.init();
        var firstCredentials = basicAuthService.configuration().credentials();

        // When – simulate a second startup with the same YAML config
        basicAuthService.init();
        var secondCredentials = basicAuthService.configuration().credentials();

        // Then – no re-save occurred: the stored hash is identical
        assertThat(secondCredentials).isEqualTo(firstCredentials);
    }

    @Test
    void shouldAuthenticate_withCorrectPassword() {
        // Given
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(tmpSettingsRepo, yamlBasicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());
        basicAuthService.init();

        // When / Then – correct credentials accepted
        HttpRequest<?> validRequest = HttpRequest.GET("/test")
            .basicAuth("admin@kestra.io", "Kestra123");
        assertThat(basicAuthService.isAuthenticated(validRequest)).isTrue();
    }

    @Test
    void shouldNotAuthenticate_withWrongPassword() {
        // Given
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(tmpSettingsRepo, yamlBasicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());
        basicAuthService.init();

        // When / Then – wrong password rejected
        HttpRequest<?> badRequest = HttpRequest.GET("/test")
            .basicAuth("admin@kestra.io", "WrongPassword1");
        assertThat(basicAuthService.isAuthenticated(badRequest)).isFalse();
    }

    @Test
    void shouldInvalidateCache_whenPasswordChanges() {
        // Given – initialised with password "Kestra123"
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(tmpSettingsRepo, yamlBasicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());
        basicAuthService.init();

        HttpRequest<?> oldPasswordRequest = HttpRequest.GET("/test")
            .basicAuth("admin@kestra.io", "Kestra123");

        // Cache a positive verification
        assertThat(basicAuthService.isAuthenticated(oldPasswordRequest)).isTrue();

        // When – password is changed
        basicAuthService.save(new BasicAuthCredentials(null, "admin@kestra.io", "NewPassword1"));

        // Then – old password no longer accepted (cache must have been invalidated)
        assertThat(basicAuthService.isAuthenticated(oldPasswordRequest)).isFalse();

        // And new password works
        HttpRequest<?> newPasswordRequest = HttpRequest.GET("/test")
            .basicAuth("admin@kestra.io", "NewPassword1");
        assertThat(basicAuthService.isAuthenticated(newPasswordRequest)).isTrue();
    }

    @Test
    void shouldRejectAuthentication_withUnmigratedSha512StoredPassword() {
        // Given – simulate a pre-migration row where the password is stored as plain SHA-512
        var tmpSettingsRepo = new InMemorySettingRepository();
        String salt = AuthUtils.generateSalt();
        String sha512Hash = AuthUtils.encodePassword(salt, "Kestra123");
        tmpSettingsRepo.save(
            Setting.builder()
                .key(BASIC_AUTH_SETTINGS_KEY)
                .value(new BasicAuthService.SaltedBasicAuthCredentials(salt, "admin@kestra.io", sha512Hash))
                .build()
        );
        // Service without calling init() — migration skipped, SHA-512 hash stays in repo
        var basicAuthService = new BasicAuthService(tmpSettingsRepo, null, instanceService, ApplicationEventPublisher.noOp());

        // When / Then – isAuthenticated must fail closed for an unmigrated SHA-512 hash
        HttpRequest<?> request = HttpRequest.GET("/test")
            .basicAuth("admin@kestra.io", "Kestra123");
        assertThat(basicAuthService.isAuthenticated(request))
            .as("pre-migration SHA-512 hash must not grant access (fail closed)")
            .isFalse();
    }

    @Test
    void shouldAuthenticate_usingBase64CookieToken() {
        // Given
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(tmpSettingsRepo, yamlBasicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());
        basicAuthService.init();

        // When – token is a Base64-encoded "username:password" (cookie format)
        String token = Base64.getEncoder().encodeToString("admin@kestra.io:Kestra123".getBytes());
        HttpRequest<?> cookieRequest = HttpRequest.GET("/test")
            .cookie(io.micronaut.http.cookie.Cookie.of(BasicAuthService.BASIC_AUTH_COOKIE_NAME, token));

        // Then
        assertThat(basicAuthService.isAuthenticated(cookieRequest)).isTrue();
    }

    @Test
    void initFromYamlConfig() throws TimeoutException {
        stubFor(
            post(urlEqualTo("/v1/reports/events"))
                .willReturn(aResponse().withStatus(200))
        );

        var settingRepositoryInterface = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(settingRepositoryInterface, yamlBasicAuthConfiguration, instanceService, ossAuthEventPublisher);
        basicAuthService.init();
        assertConfigurationMatchesApplicationYaml(basicAuthService, settingRepositoryInterface);

        awaitOssAuthEventApiCall("admin@kestra.io");
    }

    @MethodSource("getConfigs")
    @ParameterizedTest
    void should_no_save_config_at_init(ConfigWrapper configWrapper) {
        var tmpSettingsRepo = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(tmpSettingsRepo, configWrapper.config, instanceService, ApplicationEventPublisher.noOp());
        basicAuthService.init();
        assertThat(basicAuthService.configuration().credentials()).isNull();
    }

    static Stream<ConfigWrapper> getConfigs() {
        return Stream.of(
            new ConfigWrapper(null),
            new ConfigWrapper(new BasicAuthConfiguration(null, null, null, null)),
            new ConfigWrapper(new BasicAuthConfiguration(null, PASSWORD, null, null)),
            new ConfigWrapper(new BasicAuthConfiguration("", PASSWORD, null, null)),
            new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, null, null, null)),
            new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, "", null, null))
        );
    }

    @Test
    void saveValidAuthConfig() throws TimeoutException {
        stubFor(
            post(urlEqualTo("/v1/reports/events"))
                .willReturn(aResponse().withStatus(200))
        );
        var settingRepositoryInterface = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(settingRepositoryInterface, yamlBasicAuthConfiguration, instanceService, ossAuthEventPublisher);
        basicAuthService.init();
        basicAuthService.save(new BasicAuthCredentials(null, USER_NAME, PASSWORD));
        awaitOssAuthEventApiCall(USER_NAME);
    }

    @Test
    void should_throw_exception_when_saving_invalid_config() {
        var settingRepositoryInterface = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(settingRepositoryInterface, yamlBasicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());
        basicAuthService.init();
        assertThrows(ValidationErrorException.class, () -> basicAuthService.save(new BasicAuthCredentials(null, null, null)));
    }

    @MethodSource("invalidConfigs")
    @ParameterizedTest
    void should_save_error_when_validation_errors(ConfigWrapper configWrapper, String errorMessage) {
        var settingRepositoryInterface = new InMemorySettingRepository();
        var basicAuthService = new BasicAuthService(settingRepositoryInterface, configWrapper.config, instanceService, ApplicationEventPublisher.noOp());
        basicAuthService.init();
        List<String> errors = basicAuthService.validationErrors();
        assertThat(errors).containsExactly(errorMessage);
    }

    static Stream<Arguments> invalidConfigs() {
        return Stream.of(
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration("username", PASSWORD, null, null)), "Invalid username for Basic Authentication. Please provide a valid email address."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(null, PASSWORD, null, null)), "No user name set for Basic Authentication. Please provide a user name."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME + "a".repeat(244), PASSWORD, null, null)), "The length of email or password should not exceed 256 characters."),
            Arguments.of(
                new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, "pas", null, null)),
                "Invalid password for Basic Authentication. The password must have 8 chars, one upper, one lower and one number"
            ),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, null, null, null)), "No password set for Basic Authentication. Please provide a password."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, PASSWORD + "a".repeat(246), null, null)), "The length of email or password should not exceed 256 characters.")

        );
    }

    @Test
    void should_remove_validation_error_when_init_with_correct_config() {
        var settingRepositoryInterface = new InMemorySettingRepository();
        settingRepositoryInterface.save(Setting.builder().key(BASIC_AUTH_ERROR_CONFIG).value(List.of("errors")).build());

        var basicAuthService = new BasicAuthService(settingRepositoryInterface, yamlBasicAuthConfiguration, instanceService, ApplicationEventPublisher.noOp());
        basicAuthService.init();
        List<String> errors = basicAuthService.validationErrors();
        assertThat(errors).isEmpty();
    }

    private void assertConfigurationMatchesApplicationYaml(BasicAuthService basicAuthService, SettingRepositoryInterface settingRepositoryInterface) {
        var actualConfiguration = basicAuthService.configuration().credentials();

        // bcrypt is non-deterministic so we cannot compare hash values directly.
        // Verify the stored hash is a bcrypt string and the YAML password still verifies against it.
        assertThat(actualConfiguration.getUsername()).isEqualTo(basicAuthService.basicAuthConfiguration.getUsername());
        assertThat(actualConfiguration.getPassword()).startsWith("$2");
        assertThat(AuthUtils.matches(
            actualConfiguration.getSalt(),
            basicAuthService.basicAuthConfiguration.getPassword(),
            actualConfiguration.getPassword()
        )).as("stored bcrypt hash must verify against the YAML password").isTrue();

        Optional<Setting> maybeSetting = settingRepositoryInterface.findByKey(
            BASIC_AUTH_SETTINGS_KEY
        );
        assertThat(maybeSetting.isPresent()).isTrue();
    }

    private void awaitOssAuthEventApiCall(String email) {
        AtomicReference<AssertionError> lastAssertionError = new AtomicReference<>();
        try {
            Awaitility.await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(20))
                .until(() ->
                {
                    try {
                        verify(
                            new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN_OR_EQUAL, 1),
                            postRequestedFor(urlEqualTo("/v1/reports/events"))
                                .withRequestBody(
                                    and(
                                        matchingJsonPath("$.iid", equalTo(instanceService.fetch())),
                                        matchingJsonPath("$.type", equalTo(Event.EventType.OSS_AUTH.name())),
                                        matchingJsonPath("$.ossAuth.email", equalTo(email))
                                    )
                                )
                        );
                        return true;
                    } catch (AssertionError e) {
                        lastAssertionError.set(e);
                        return false;
                    }
                });
        } catch (ConditionTimeoutException e) {
            fail("awaitOssAuthEventApiCall timeout, last error: " + lastAssertionError.get().getMessage());
        }
    }

    //Useful because micronaut tries to inject the configuration and made a multiple competing ParameterResolvers exception
    record ConfigWrapper(BasicAuthConfiguration config) {
    }
}
