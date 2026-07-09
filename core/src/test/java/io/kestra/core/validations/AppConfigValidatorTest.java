package io.kestra.core.validations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.BeanInstantiationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppConfigValidatorTest {

    @Test
    void validateNoKestraUrl() {
        assertThatCode(() ->
        {
            try (ApplicationContext context = ApplicationContext.run()) {
                context.getBean(AppConfigValidator.class);
            }
        })
            .as("The bean got initialized properly including the PostConstruct validation")
            .doesNotThrowAnyException();
    }

    @Test
    void validateValidKestraUrl() {
        assertThatCode(() ->
        {
            try (
                ApplicationContext context = ApplicationContext.builder()
                    .deduceEnvironment(false)
                    .environments("test")
                    .properties(
                        Map.of("kestra.url", "https://postgres-oss.preview.dev.kestra.io")
                    )
                    .start()
            ) {
                context.getBean(AppConfigValidator.class);
            }
        })
            .as("The bean got initialized properly including the PostConstruct validation")
            .doesNotThrowAnyException();
    }

    @Test
    void validateInvalidKestraUrl() {
        assertThatThrownBy(() ->
        {
            try (
                ApplicationContext context = ApplicationContext.builder()
                    .deduceEnvironment(false)
                    .environments("test")
                    .properties(
                        Map.of("kestra.url", "postgres-oss.preview.dev.kestra.io")
                    )
                    .start()
            ) {
                context.getBean(AppConfigValidator.class);
            }
        })
            .as("The bean initialization failed at PostConstruct")
            .isInstanceOf(BeanInstantiationException.class)
            .hasMessageContaining("Invalid configuration");
    }

    @Test
    void validateNonHttpKestraUrl() {
        assertThatThrownBy(() ->
        {
            try (
                ApplicationContext context = ApplicationContext.builder()
                    .deduceEnvironment(false)
                    .environments("test")
                    .properties(
                        Map.of("kestra.url", "ftp://postgres-oss.preview.dev.kestra.io")
                    )
                    .start()
            ) {
                context.getBean(AppConfigValidator.class);
            }
        })
            .as("The bean initialization failed at PostConstruct")
            .isInstanceOf(BeanInstantiationException.class)
            .hasMessageContaining("Invalid configuration");
    }

    @Test
    void shouldReturnValidResultWhenKestraUrlAbsent() {
        Environment environment = mock(Environment.class);
        when(environment.containsProperty("kestra.url")).thenReturn(false);

        List<ConfigValidationResult> results = AppConfigValidator.validateConfiguration(environment);

        assertThat(results)
            .as("An absent kestra.url is valid")
            .allMatch(ConfigValidationResult::valid);
    }

    @Test
    void shouldReturnValidResultWhenKestraUrlValid() {
        Environment environment = mock(Environment.class);
        when(environment.containsProperty("kestra.url")).thenReturn(true);
        when(environment.getProperty("kestra.url", String.class)).thenReturn(Optional.of("https://your.company.com"));

        List<ConfigValidationResult> results = AppConfigValidator.validateConfiguration(environment);

        assertThat(results)
            .as("A well-formed HTTPS kestra.url is valid")
            .allMatch(ConfigValidationResult::valid);
    }

    @Test
    void shouldReturnInvalidResultWhenKestraUrlMalformed() {
        Environment environment = mock(Environment.class);
        when(environment.containsProperty("kestra.url")).thenReturn(true);
        when(environment.getProperty("kestra.url", String.class)).thenReturn(Optional.of("not a url"));

        List<ConfigValidationResult> results = AppConfigValidator.validateConfiguration(environment);

        assertThat(results)
            .as("A malformed kestra.url is reported as invalid with a message")
            .anyMatch(result -> !result.valid() && result.key().equals("kestra.url") && result.message() != null);
    }

    @Test
    void shouldReturnInvalidResultWhenKestraUrlNotHttp() {
        Environment environment = mock(Environment.class);
        when(environment.containsProperty("kestra.url")).thenReturn(true);
        when(environment.getProperty("kestra.url", String.class)).thenReturn(Optional.of("ftp://your.company.com"));

        List<ConfigValidationResult> results = AppConfigValidator.validateConfiguration(environment);

        assertThat(results)
            .as("A non-HTTP kestra.url scheme is reported as invalid")
            .anyMatch(result -> !result.valid() && result.key().equals("kestra.url"));
    }
}