package io.kestra.webserver.services;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.Await;
import io.kestra.webserver.models.events.Event;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@WireMockTest(httpPort = 28181)
class BasicAuthServiceTest {
    private BasicAuthService basicAuthService;

    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    private SettingRepositoryInterface settingRepositoryInterface;

    private InstanceService instanceService;

    private ApplicationContext ctx;

    @BeforeEach
    void mockEventsAndStartApp() {
        stubFor(
            post(urlEqualTo("/v1/reports/events"))
                .willReturn(aResponse().withStatus(200))
        );
        ctx = ApplicationContext.run(Map.of("kestra.server.basic-auth.enabled", "true"), Environment.TEST);

        basicAuthService = ctx.getBean(BasicAuthService.class);
        basicAuthConfiguration = ctx.getBean(BasicAuthService.BasicAuthConfiguration.class);
        settingRepositoryInterface = ctx.getBean(SettingRepositoryInterface.class);
        instanceService = ctx.getBean(InstanceService.class);
    }

    @AfterEach
    void stopApp() {
        settingRepositoryInterface.delete(Setting.builder().key(BasicAuthService.BASIC_AUTH_SETTINGS_KEY).build());

        ctx.stop();
    }

    @Test
    void initFromYamlConfig() throws TimeoutException {
        assertThat(basicAuthService.isEnabled(), is(true));

        assertConfigurationMatchesApplicationYaml();

        awaitOssAuthEventApiCall("admin@kestra.io");
    }

    @Test
    void secure() throws TimeoutException {
        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> basicAuthService.save(basicAuthConfiguration.withUsernamePassword("not-an-email", "password"))
        );

        assertThat(illegalArgumentException.getMessage(), is("Invalid username for Basic Authentication. Please provide a valid email address."));

        assertConfigurationMatchesApplicationYaml();

        basicAuthService.save(basicAuthConfiguration.withUsernamePassword("some@email.com", "password"));
        awaitOssAuthEventApiCall("some@email.com");
    }

    @Test
    void unsecure() {
        assertThat(basicAuthService.isEnabled(), is(true));
        BasicAuthService.SaltedBasicAuthConfiguration previousConfiguration = basicAuthService.configuration();

        basicAuthService.unsecure();

        assertThat(basicAuthService.isEnabled(), is(false));
        BasicAuthService.SaltedBasicAuthConfiguration newConfiguration = basicAuthService.configuration();


        assertThat(newConfiguration.getEnabled(), is(false));
        assertThat(newConfiguration.getUsername(), is(previousConfiguration.getUsername()));
        assertThat(newConfiguration.getPassword(), is(previousConfiguration.getPassword()));
        assertThat(newConfiguration.getRealm(), is(previousConfiguration.getRealm()));
        assertThat(newConfiguration.getOpenUrls(), is(previousConfiguration.getOpenUrls()));
    }

    private void assertConfigurationMatchesApplicationYaml() {
        BasicAuthService.SaltedBasicAuthConfiguration actualConfiguration = basicAuthService.configuration();
        BasicAuthService.SaltedBasicAuthConfiguration applicationYamlConfiguration = new BasicAuthService.SaltedBasicAuthConfiguration(
            actualConfiguration.getSalt(),
            basicAuthConfiguration
        );
        assertThat(actualConfiguration, is(applicationYamlConfiguration));

        Optional<Setting> maybeSetting = settingRepositoryInterface.findByKey(BasicAuthService.BASIC_AUTH_SETTINGS_KEY);
        assertThat(maybeSetting.isPresent(), is(true));
        assertThat(maybeSetting.get().getValue(), is(JacksonMapper.toMap(applicationYamlConfiguration)));
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
        }, Duration.ofMillis(100), Duration.ofSeconds(10));
    }
}
