package io.kestra.webserver.services;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.Await;
import io.kestra.webserver.models.events.Event;
import io.kestra.webserver.services.BasicAuthService.BasicAuthConfiguration;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_ERROR_CONFIG;
import static io.kestra.webserver.services.BasicAuthService.BASIC_AUTH_SETTINGS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest(httpPort = 28181)
class BasicAuthServiceTest {

    public static final String PASSWORD = "Password123";
    public static final String USER_NAME = "user@kestra.io";

    private BasicAuthService basicAuthService;

    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    private SettingRepositoryInterface settingRepositoryInterface;

    private InstanceService instanceService;

    private ApplicationContext ctx;

    @SneakyThrows
    @BeforeEach
    void mockEventsAndStartApp() {
        stubFor(
            post(urlEqualTo("/v1/reports/events"))
                .willReturn(aResponse().withStatus(200))
        );

        //A sleep is required here because Kestra may start up before wiremock is ready, leading in the event we are looking for being missed
        Thread.sleep(500L);

        ctx = ApplicationContext.run(Map.of(), Environment.TEST);

        basicAuthService = ctx.getBean(BasicAuthService.class);
        basicAuthConfiguration = ctx.getBean(BasicAuthService.BasicAuthConfiguration.class);
        settingRepositoryInterface = ctx.getBean(SettingRepositoryInterface.class);
        instanceService = ctx.getBean(InstanceService.class);
    }

    @AfterEach
    void stopApp() {
        deleteSetting();
        ctx.stop();
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
        assertConfigurationMatchesApplicationYaml();

        awaitOssAuthEventApiCall("admin@kestra.io");
    }
    @MethodSource("getConfigs")
    @ParameterizedTest
    void should_no_save_config_at_init(BasicAuthConfiguration config){
        deleteSetting();
        basicAuthService.setBasicAuthConfiguration(config);
        basicAuthService.init();
        assertThat(basicAuthService.configuration()).isNull();
    }

    static Stream<BasicAuthConfiguration> getConfigs() {
        return Stream.of(
            null,
            new BasicAuthConfiguration(null, null),
            new BasicAuthConfiguration(null, PASSWORD),
            new BasicAuthConfiguration("", PASSWORD),
            new BasicAuthConfiguration(USER_NAME, null),
            new BasicAuthConfiguration(USER_NAME, "")
        );
    }

    @Test
    void saveValidAuthConfig() throws TimeoutException {
        basicAuthService.save(basicAuthConfiguration.withUsernamePassword(USER_NAME, PASSWORD));
        awaitOssAuthEventApiCall(USER_NAME);
    }

    @Test
    void should_throw_exception_when_saving_invalid_config() {
        assertThrows(ValidationErrorException.class, () -> basicAuthService.save(new BasicAuthConfiguration(null, null)));
    }

    @MethodSource("invalidConfigs")
    @ParameterizedTest
    void should_save_error_when_validation_errors(BasicAuthConfiguration config, String errorMessage){
        deleteSetting();
        basicAuthService.setBasicAuthConfiguration(config);
        basicAuthService.init();
        List<String> errors = basicAuthService.validationErrors();
        assertThat(errors).containsExactly(errorMessage);
    }

    static Stream<Arguments> invalidConfigs() {
        return Stream.of(
            Arguments.of(new BasicAuthConfiguration("username", PASSWORD), "Invalid username for Basic Authentication. Please provide a valid email address."),
            Arguments.of(new BasicAuthConfiguration(null, PASSWORD), "No user name set for Basic Authentication. Please provide a user name."),
            Arguments.of(new BasicAuthConfiguration(USER_NAME + "a".repeat(244), PASSWORD), "The length of email or password should not exceed 256 characters."),
            Arguments.of(new BasicAuthConfiguration(USER_NAME, "pas"), "Invalid password for Basic Authentication. The password must have 8 chars, one upper, one lower and one number"),
            Arguments.of(new BasicAuthConfiguration(USER_NAME, null), "No password set for Basic Authentication. Please provide a password."),
            Arguments.of(new BasicAuthConfiguration(USER_NAME, PASSWORD + "a".repeat(246)), "The length of email or password should not exceed 256 characters.")

        );
    }

    @Test
    void should_remove_validation_error_when_init_with_correct_config(){
        deleteSetting();
        settingRepositoryInterface.save(Setting.builder().key(BASIC_AUTH_ERROR_CONFIG).value(List.of("errors")).build());
        basicAuthService.init();
        List<String> errors = basicAuthService.validationErrors();
        assertThat(errors).isEmpty();
    }

    private void assertConfigurationMatchesApplicationYaml() {
        BasicAuthService.SaltedBasicAuthConfiguration actualConfiguration = basicAuthService.configuration();
        BasicAuthService.SaltedBasicAuthConfiguration applicationYamlConfiguration = new BasicAuthService.SaltedBasicAuthConfiguration(
            actualConfiguration.getSalt(),
            basicAuthConfiguration
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
}
