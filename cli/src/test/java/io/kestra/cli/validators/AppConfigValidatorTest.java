package io.kestra.cli.validators;

import io.kestra.cli.AppConfig;
import io.kestra.core.models.validations.ModelValidator;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.BeanInstantiationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigValidatorTest {
    @Test
    void validConfig() {
        final Map<String, Object> props = Map.of("kestra.url", "https://postgres-oss.preview.dev.kestra.io");

        try (ApplicationContext context = ApplicationContext.builder(Environment.CLI, Environment.TEST).properties(props).start()) {
            ModelValidator validator = context.getBean(ModelValidator.class);
            AppConfig config = context.getBean(AppConfig.class);

            assertThat(validator.isValid(config)).isEmpty();
        }
    }

    @Test
    void invalidConfig() {
        final Map<String, Object> props = Map.of("kestra.url", "foo.bar");

        try (ApplicationContext context = ApplicationContext.builder().deduceEnvironment(false).properties(props).start()) {
            final Throwable exception = assertThrows(BeanInstantiationException.class, () ->
                context.getBean(AppConfig.class)
            );

            assertThat(exception.getMessage()).contains("'kestra.url' configuration property must be a valid HTTP(S) URL");
        }
    }
}
