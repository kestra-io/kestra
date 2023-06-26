package io.kestra.webserver.controllers;

import io.kestra.core.models.Setting;
import io.kestra.core.models.collectors.ExecutionUsage;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class MiscControllerTest extends AbstractMemoryRunnerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    @Test
    void ping() {
        var response = client.toBlocking().retrieve("/ping", String.class);

        assertThat(response, is("pong"));
    }

    @Test
    void configuration() {
        var response = client.toBlocking().retrieve("/api/v1/configs", MiscController.Configuration.class);

        assertThat(response, notNullValue());
        assertThat(response.getUuid(), notNullValue());
        assertThat(response.getIsTaskRunEnabled(), is(false));
        assertThat(response.getIsAnonymousUsageEnabled(), is(true));
    }

    @Test
    void executionUsage() {
        var response = client.toBlocking().retrieve("/api/v1/execution-usage", ExecutionUsage.class);

        assertThat(response, notNullValue());
        // the memory executor didn't support daily statistics so we cannot have real execution usage
    }

    @MockBean
    SettingRepositoryInterface settingRepository() {
        return new SettingRepositoryInterface() {
            @Override
            public Optional<Setting> findByKey(String key) {
                return Optional.empty();
            }

            @Override
            public List<Setting> findAll() {
                return Collections.emptyList();
            }

            @Override
            public Setting save(Setting setting) throws ConstraintViolationException {
                return setting;
            }

            @Override
            public Setting delete(Setting setting) {
                return setting;
            }
        };
    }
}