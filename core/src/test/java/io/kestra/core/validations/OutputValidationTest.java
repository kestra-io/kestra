package io.kestra.core.validations;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.plugin.core.log.Log;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class OutputValidationTest {
    @Inject
    private ModelValidator modelValidator;

    private static Output output(Type type, Boolean searchable) {
        return Output.builder().id("output").type(type).value("{{ outputs.log }}").searchable(searchable).build();
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = {"STRING", "EMAIL", "SELECT", "INT", "BOOL", "FLOAT", "URI", "DATETIME", "DATE", "TIME", "DURATION", "ARRAY"})
    void shouldAcceptASearchableOutputOfASupportedType(Type type) {
        assertThat(modelValidator.isValid(output(type, true))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = {"JSON", "FILE", "SECRET", "MULTISELECT", "YAML", "ION", "FORM", "TABLE", "REUSABLE_INPUTS"})
    void shouldRejectASearchableOutputOfAnUnsupportedType(Type type) {
        Optional<ConstraintViolationException> violation = modelValidator.isValid(output(type, true));

        assertThat(violation).isPresent();
        assertThat(violation.get().getMessage()).contains("The output 'output' cannot be searchable").contains(type.name());
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = {"JSON", "FILE", "SECRET"})
    void shouldAcceptAnyTypeWhenTheOutputIsNotSearchable(Type type) {
        assertThat(modelValidator.isValid(output(type, false))).isEmpty();
        assertThat(modelValidator.isValid(output(type, null))).isEmpty();
    }

    @Test
    void shouldRejectAFlowWithASearchableOutputOfAnUnsupportedType() {
        Flow flow = Flow.builder()
            .id("flow")
            .namespace("io.kestra.tests")
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()))
            .outputs(List.of(output(Type.JSON, true)))
            .build();

        Optional<ConstraintViolationException> violation = modelValidator.isValid(flow);

        assertThat(violation).isPresent();
        assertThat(violation.get().getMessage()).contains("The output 'output' cannot be searchable");
    }
}
