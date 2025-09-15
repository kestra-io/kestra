package io.kestra.webserver.services;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.and;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_ERROR_CONFIG;
import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_SETTINGS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.Await;
import io.kestra.webserver.models.events.Event;
import io.kestra.webserver.services.BasicAuthService.BasicAuthConfiguration;
import io.micronaut.context.env.Environment;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        settingRepositoryInterface.save(Setting.builder()
            .key(BASIC_AUTH_SETTINGS_KEY)
            .value(new BasicAuthConfiguration("username", "password", null, null))
            .build());
        assertTrue(basicAuthService.isBasicAuthInitialized());

        deleteSetting();
        assertFalse(basicAuthService.isBasicAuthInitialized());

        settingRepositoryInterface.save(Setting.builder()
            .key(BASIC_AUTH_SETTINGS_KEY)
            .value(new BasicAuthConfiguration("username", null, null, null))
            .build());
        assertFalse(basicAuthService.isBasicAuthInitialized());

        settingRepositoryInterface.save(Setting.builder()
            .key(BASIC_AUTH_SETTINGS_KEY)
            .value(new BasicAuthConfiguration(null, null, null, null))
            .build());
        assertFalse(basicAuthService.isBasicAuthInitialized());
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
        assertThat(basicAuthService.configuration()).isNull();
    }

    static Stream<ConfigWrapper> getConfigs() {
        return Stream.of(
            new ConfigWrapper(null),
            new ConfigWrapper(new BasicAuthConfiguration(null, null)),
            new ConfigWrapper(new BasicAuthConfiguration(null, PASSWORD)),
            new ConfigWrapper(new BasicAuthConfiguration("", PASSWORD)),
            new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, null)),
            new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, ""))
        );
    }

    @Test
    void saveValidAuthConfig() throws TimeoutException {
        basicAuthService.save(new BasicAuthConfiguration(USER_NAME, PASSWORD));
        awaitOssAuthEventApiCall(USER_NAME);
    }

    @Test
    void should_throw_exception_when_saving_invalid_config() {
        assertThrows(ValidationErrorException.class, () -> basicAuthService.save(new BasicAuthConfiguration(null, null)));
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
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration("username", PASSWORD)), "Invalid username for Basic Authentication. Please provide a valid email address."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(null, PASSWORD)), "No user name set for Basic Authentication. Please provide a user name."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME + "a".repeat(244), PASSWORD)), "The length of email or password should not exceed 256 characters."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, "pas")), "Invalid password for Basic Authentication. The password must have 8 chars, one upper, one lower and one number"),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, null)), "No password set for Basic Authentication. Please provide a password."),
            Arguments.of(new ConfigWrapper(new BasicAuthConfiguration(USER_NAME, PASSWORD + "a".repeat(246))), "The length of email or password should not exceed 256 characters.")

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
        BasicAuthService.SaltedBasicAuthConfiguration actualConfiguration = basicAuthService.configuration();
        BasicAuthService.SaltedBasicAuthConfiguration applicationYamlConfiguration = new BasicAuthService.SaltedBasicAuthConfiguration(
            actualConfiguration.getSalt(),
            basicAuthService.basicAuthConfiguration
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
