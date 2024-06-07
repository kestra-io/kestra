package io.kestra.core.validations;

import io.kestra.core.models.validations.ModelValidator;
import io.micronaut.core.annotation.Introspected;
import io.kestra.core.junit.annotations.KestraTest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

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

        assertThat(modelValidator.isValid(build).isEmpty(), is(true));

        build = new JsonStringCls("{\"invalid\"}");

        assertThat(modelValidator.isValid(build).isPresent(), is(true));
        assertThat(modelValidator.isValid(build).get().getMessage(), containsString("invalid json"));
    }
}
