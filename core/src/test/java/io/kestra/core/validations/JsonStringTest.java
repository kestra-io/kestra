package io.kestra.core.validations;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;

import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class JsonStringTest {
    @Inject
    private ModelValidator modelValidator;

    @AllArgsConstructor
    @Introspected
    @Getter
    public static class JsonStringCls {
        @JsonString
        String json;
    }

    @Test
    void jsonString() throws Exception {
        JsonStringCls build = new JsonStringCls("{}");

        assertThat(modelValidator.isValid(build).isEmpty()).isTrue();

        build = new JsonStringCls("{\"invalid\"}");

        assertThat(modelValidator.isValid(build).isPresent()).isTrue();
        assertThat(modelValidator.isValid(build).get().getMessage()).contains("invalid json");
    }
}
