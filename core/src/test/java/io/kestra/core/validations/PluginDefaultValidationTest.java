package io.kestra.core.validations;

import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PluginDefaultValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void nullValuesShouldViolate() {
        PluginDefault pluginDefault = PluginDefault.builder()
            .type("io.kestra.tests")
            .build();

        Optional<ConstraintViolationException> validate = modelValidator.isValid(pluginDefault);

        assertThat(validate.isPresent()).isTrue();
    }

    @Test
    void nullPropertiesShouldViolate() {
        Map<String, Object> props = new HashMap<>();
        props.put("nullProperty", null);
        PluginDefault pluginDefault = PluginDefault.builder()
            .type("io.kestra.tests")
            .values(props)
            .build();

        Optional<ConstraintViolationException> validate = modelValidator.isValid(pluginDefault);

        assertThat(validate.isPresent()).isTrue();
    }

    @Test
    void unknownPropertyShouldViolate() {
        PluginDefault pluginDefault = PluginDefault.builder()
            .type("io.kestra.plugin.core.log.Log")
            .values(Map.of("not", "existing"))
            .build();

        Optional<ConstraintViolationException> validate = modelValidator.isValid(pluginDefault);

        assertThat(validate.isPresent()).isTrue();
    }

    @Test
    void unknownPropertyOnUnknownPluginShouldPass() {
        PluginDefault pluginDefault = PluginDefault.builder()
            .type("io.kestra.plugin.core.log")
            .values(Map.of("not", "existing"))
            .build();

        Optional<ConstraintViolationException> validate = modelValidator.isValid(pluginDefault);

        assertThat(validate.isEmpty()).isTrue();
    }

    @Test
    void validShouldPass() {
        PluginDefault pluginDefault = PluginDefault.builder()
            .type("io.kestra.plugin.core.log.Log")
            .values(Map.of("level", "WARN"))
            .build();

        Optional<ConstraintViolationException> validate = modelValidator.isValid(pluginDefault);

        assertThat(validate.isEmpty()).isTrue();
    }

}
