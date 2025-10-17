package io.kestra.webserver.services;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.AuthUtils;
import io.kestra.core.utils.Await;
import io.kestra.webserver.controllers.api.MiscController;
import io.kestra.webserver.models.events.Event;
import io.kestra.webserver.services.BasicAuthService.BasicAuthConfiguration;
import io.micronaut.context.env.Environment;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_ERROR_CONFIG;
import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_SETTINGS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest(httpPort = 28181)
@KestraTest(environments = Environment.TEST)
class BasicAuthServiceTest {

    public static final String PASSWORD = "Password123";
    public static final String USER_NAME = "user@kestra.io";

    @Inject
    private BasicAuthService basicAuthService;

    @Inject
    private BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private SettingRepositoryInterface settingRepositoryInterface;

    @Inject
    private InstanceService instanceService;

    @AfterEach
    void stopApp() {
        stubFor(
            post(urlEqualTo("/v1/reports/events"))
                .willReturn(aResponse().withStatus(200))
        );
        deleteSetting();
    }

    @Test
    void isBasicAuthInitialized(){
        deleteSetting();
        basicAuthService.basicAuthConfiguration = new ConfigWrapper(
            new BasicAuthConfiguration(USER_NAME, PASSWORD, null, null)
        ).config;
        basicAuthService.init();
        assertTrue(basicAuthService.isBasicAuthInitialized());

        deleteSetting();
        assertFalse(basicAuthService.isBasicAuthInitialized());

        basicAuthService.basicAuthConfiguration = new ConfigWrapper(
            new BasicAuthConfiguration(USER_NAME, null, null, null)
        ).config;
        basicAuthService.init();
        assertFalse(basicAuthService.isBasicAuthInitialized());

        basicAuthService.basicAuthConfiguration = new ConfigWrapper(
            new BasicAuthConfiguration(null, null, null, null)
        ).config;
        basicAuthService.init();
        assertFalse(basicAuthService.isBasicAuthInitialized());
    }

    @Test
    void basicAuthAPICreation_shouldNot_discardYamlConfiguration(){
        // simulate starting Kestra for the first time
        deleteSetting();
        var defaultConfigWithoutBasicAuthCreds = new ConfigWrapper(
            new BasicAuthConfiguration(null, null, "Kestra2", List.of("/api/v1/main/executions/webhook/"))
        );
        basicAuthService.basicAuthConfiguration = defaultConfigWithoutBasicAuthCreds.config;
        basicAuthService.init();
        assertFalse(basicAuthService.isBasicAuthInitialized());

        /**
         * simulate basic auth UI onboarding (createBasicAuth)
         * {@link io.kestra.webserver.controllers.api.MiscController#createBasicAuth(MiscController.BasicAuthCredentials)}
         */
        basicAuthService.save(
            new BasicAuthCredentials(
                BASIC_AUTH_SETTINGS_KEY,
                "username1@example.com",
                "Password1"
            )
        );
        assertTrue(basicAuthService.isBasicAuthInitialized());

        assertThat(basicAuthService.configuration())
            .as("Default configured realm and openUrls should not have been discarded after creating the basic auth user")
            .satisfies(configuration -> {
                assertThat(configuration.credentials().getUsername()).isEqualTo("username1@example.com");
                assertThat(configuration.credentials().getPassword()).isNotBlank();
                assertThat(configuration.realm()).isEqualTo("Kestra2");
                assertThat(configuration.openUrls()).isEqualTo(List.of("/api/v1/main/executions/webhook/"));
            });
    }

    @Test
    void basicAuthAPICreation_shouldNot_discardYamlConfiguration_andBeBackwardCompatible_noDefaultCredentials() {
        // simulate starting Kestra for the first time
        deleteSetting();
        var defaultConfigWithoutBasicAuthCreds = new ConfigWrapper(
            new BasicAuthConfiguration(null, null, "Kestra2", List.of("/api/v1/main/executions/webhook/"))
        );
        basicAuthService.basicAuthConfiguration = defaultConfigWithoutBasicAuthCreds.config;
        settingRepositoryInterface.save(Setting.builder()
            .key(BASIC_AUTH_SETTINGS_KEY)
            .value(BasicAuthService.SaltedBasicAuthCredentials.salt(null, "username1@example.com", "Password1"))
            .build());
        assertTrue(basicAuthService.isBasicAuthInitialized());
        basicAuthService.init();
        assertTrue(basicAuthService.isBasicAuthInitialized());

        assertThat(basicAuthService.configuration())
            .as("Default configured realm and openUrls should not have been discarded after creating the basic auth user")
            .satisfies(configuration -> {
                assertThat(configuration.credentials().getUsername()).isEqualTo("username1@example.com");
                assertThat(configuration.credentials().getPassword()).isNotBlank();
                assertThat(configuration.realm()).isEqualTo("Kestra2");
                assertThat(configuration.openUrls()).isEqualTo(List.of("/api/v1/main/executions/webhook/"));
            });
    }

    @Test
    void basicAuthAPICreation_shouldNot_discardYamlConfiguration_andBeBackwardCompatible_withDefaultCredentials() {
        // simulate starting Kestra for the first time
        deleteSetting();
        var defaultConfigWithoutBasicAuthCreds = new ConfigWrapper(
            new BasicAuthConfiguration("username1@example.com", "Password1", "Kestra2", List.of("/api/v1/main/executions/webhook/"))
        );
        basicAuthService.basicAuthConfiguration = defaultConfigWithoutBasicAuthCreds.config;
        basicAuthService.init();
        assertTrue(basicAuthService.isBasicAuthInitialized());

        assertThat(basicAuthService.configuration())
            .as("Default configured realm and openUrls should not have been discarded after creating the basic auth user")
            .satisfies(configuration -> {
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
        var salt = AuthUtils.generateSalt();
        settingRepositoryInterface.save(Setting.builder()
            .key(BASIC_AUTH_SETTINGS_KEY)
            .value(new LegacySaltedBasicAuthConfiguration(salt, "username1@example.com", AuthUtils.encodePassword(salt, "Password1"), "OldPersistedRealm", List.of("old-persisted-open-url")))
            .build());
        deleteSetting();

        basicAuthService.basicAuthConfiguration = new ConfigWrapper(
            new BasicAuthConfiguration("username1@example.com", "Password1", "NewRealmFromConf", List.of("NewOpenurl-fromConf"))
        ).config;
        basicAuthService.init();

        // then
        assertThat(basicAuthService.configuration())
            .as("should be able to fetch deserialize legacy configuration that contained 'realm' and 'openUrls', we do not persist these fields anymore")
            .satisfies(configuration -> {
                assertThat(configuration.credentials().getUsername()).isEqualTo("username1@example.com");
                assertThat(configuration.credentials().getPassword()).isNotBlank();
                assertThat(configuration.realm()).isEqualTo("NewRealmFromConf");
                assertThat(configuration.openUrls()).isEqualTo(List.of("NewOpenurl-fromConf"));
            });
    }

    @Test
    void initFromYamlConfig() throws TimeoutException {
        basicAuthService.basicAuthConfiguration = basicAuthConfiguration;
        basicAuthService.init();
        assertConfigurationMatchesApplicationYaml();

        awaitOssAuthEventApiCall("admin@kestra.io");
    }

    @MethodSource("getConfigs")
    @ParameterizedTest
    void should_no_save_config_at_init(ConfigWrapper configWrapper){
        deleteSetting();
        basicAuthService.basicAuthConfiguration = configWrapper.config;
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
        basicAuthService.save(new BasicAuthCredentials(null, USER_NAME, PASSWORD));
        awaitOssAuthEventApiCall(USER_NAME);
    }

    @Test
    void should_throw_exception_when_saving_invalid_config() {
        assertThrows(ValidationErrorException.class, () -> basicAuthService.save(new BasicAuthCredentials(null, null, null)));
    }

    @MethodSource("invalidConfigs")
    @ParameterizedTest
    void should_save_error_when_validation_errors(ConfigWrapper configWrapper, String errorMessage){
        deleteSetting();
        basicAuthService.basicAuthConfiguration = configWrapper.config;
        basicAuthService.init();
        List<String> errors = basicAuthService.validationErrors();
        assertThat(errors).containsExactly(errorMessage);
    }

    static Stream<Arguments> invalidConfigs() {
        return Stream.of(
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration("username", PASSWORD, null, null)), "Invalid username for Basic Authentication. Please provide a valid email address."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(null, PASSWORD, null, null)), "No user name set for Basic Authentication. Please provide a user name."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME + "a".repeat(244), PASSWORD, null, null)), "The length of email or password should not exceed 256 characters."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, "pas", null, null)), "Invalid password for Basic Authentication. The password must have 8 chars, one upper, one lower and one number"),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, null, null, null)), "No password set for Basic Authentication. Please provide a password."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, PASSWORD + "a".repeat(246), null, null)), "The length of email or password should not exceed 256 characters.")

        );
    }

    @Test
    void should_remove_validation_error_when_init_with_correct_config(){
        deleteSetting();
        settingRepositoryInterface.save(Setting.builder().key(BASIC_AUTH_ERROR_CONFIG).value(List.of("errors")).build());
        basicAuthService.basicAuthConfiguration = basicAuthConfiguration;
        basicAuthService.init();
        List<String> errors = basicAuthService.validationErrors();
        assertThat(errors).isEmpty();
    }

    private void assertConfigurationMatchesApplicationYaml() {
        var actualConfiguration = basicAuthService.configuration().credentials();
        var applicationYamlConfiguration = BasicAuthService.SaltedBasicAuthCredentials.salt(
            actualConfiguration.getSalt(),
            basicAuthService.basicAuthConfiguration.getUsername(),
            basicAuthService.basicAuthConfiguration.getPassword()
        );
        assertThat(actualConfiguration).isEqualTo(applicationYamlConfiguration);

        Optional<Setting> maybeSetting = settingRepositoryInterface.findByKey(
            BASIC_AUTH_SETTINGS_KEY);
        assertThat(maybeSetting.isPresent()).isTrue();
        assertThat(maybeSetting.get().getValue()).isEqualTo(JacksonMapper.toMap(applicationYamlConfiguration));
    }

    private void awaitOssAuthEventApiCall(String email) throws TimeoutException {
        Await.until(() -> {
            try {
                verify(
                    1,
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
                return false;
            }
        }, Duration.ofMillis(100), Duration.ofSeconds(20));
    }

    private void deleteSetting() {
        if (settingRepositoryInterface.findByKey(BASIC_AUTH_SETTINGS_KEY).isPresent()){
            settingRepositoryInterface.delete(
                Setting.builder().key(BASIC_AUTH_SETTINGS_KEY).build());
        }
    }

    //Useful because micronaut tries to inject the configuration and made a multiple competing ParameterResolvers exception
    record ConfigWrapper(BasicAuthConfiguration config){}
}
