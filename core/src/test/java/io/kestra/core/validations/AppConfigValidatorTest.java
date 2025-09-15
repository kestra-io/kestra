package io.kestra.core.validations;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppConfigValidatorTest {

    @Test
    void validateNoKestraUrl() {
        assertThatCode(() -> {
            try (ApplicationContext context = ApplicationContext.run()) {
                context.getBean(AppConfigValidator.class);
            }
        })
            .as("The bean got initialized properly including the PostConstruct validation")
            .doesNotThrowAnyException();
    }

    @Test
    void validateValidKestraUrl() {
        assertThatCode(() -> {
            try (ApplicationContext context = ApplicationContext.builder()
                .deduceEnvironment(false)
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
        assertThatThrownBy(() -> {
            try (ApplicationContext context = ApplicationContext.builder()
                .deduceEnvironment(false)
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
        assertThatThrownBy(() -> {
            try (ApplicationContext context = ApplicationContext.builder()
                .deduceEnvironment(false)
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
}