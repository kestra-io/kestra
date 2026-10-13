package io.kestra.core.validations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class NoSystemLabelValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @ParameterizedTest
    @ValueSource(strings = { "system.sla", "system" })
    void shouldReportAViolation(String key) {
        var sla = MaxDurationSLA.builder()
            .duration(Duration.ofSeconds(1))
            .id("id")
            .behavior(SLA.Behavior.CANCEL)
            .type(SLA.Type.MAX_DURATION)
            .labels(List.of(new Label(key, "violated")))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);

        assertThat(valid.isPresent()).isTrue();
        assertThat(valid.get().getMessage()).isEqualTo("labels[0].<list element>: System labels can only be set by Kestra itself, offending label: " + key + "=violated.\n");
    }

    @Test
    void shouldSuccess() {
        var sla = MaxDurationSLA.builder()
            .duration(Duration.ofSeconds(1))
            .id("id")
            .behavior(SLA.Behavior.CANCEL)
            .type(SLA.Type.MAX_DURATION)
            .labels(List.of(new Label("sla", "violated")))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);

        assertThat(valid.isEmpty()).isTrue();
    }
}
