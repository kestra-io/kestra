package io.kestra.core.models.validations;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateConstraintViolationTest {
    @Test
    void shouldExposeNoErrorsWhenValid() {
        var violation = ValidateConstraintViolation.builder().build();

        assertThat(violation.getErrors()).isEmpty();
        assertThat(violation.getConstraints()).isEmpty();
    }

    @Test
    void shouldSerializeDeprecatedConstraintsAsOneLinePerError() throws Exception {
        var violation = ValidateConstraintViolation.builder()
            .errors(List.of(
                new ValidationError("must not be null", "/tasks/0/message", "tasks[log].message"),
                ValidationError.of("Unable to validate the flow: boom")
            ))
            .build();

        String json = JacksonMapper.ofJson().writeValueAsString(violation);
        var roundTripped = JacksonMapper.ofJson().readValue(json, ValidateConstraintViolation.class);

        assertThat(JacksonMapper.ofJson().readTree(json).get("constraints").asText())
            .isEqualTo("tasks[log].message: must not be null\nUnable to validate the flow: boom");
        assertThat(roundTripped.getErrors()).hasSize(2);
    }
}
