package io.kestra.core.validations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowSource;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.services.FlowService;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class MaxDurationSLAValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Inject
    private FlowService flowService;

    @Test
    void shouldValidatePositiveDuration() {
        var sla = MaxDurationSLA.builder()
            .id("dur")
            .type(SLA.Type.MAX_DURATION)
            .behavior(SLA.Behavior.FAIL)
            .duration(Duration.ofHours(1))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateNegativeDuration() {
        var sla = MaxDurationSLA.builder()
            .id("dur")
            .type(SLA.Type.MAX_DURATION)
            .behavior(SLA.Behavior.FAIL)
            .duration(Duration.ofHours(-1))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getMessage()).contains("must be positive");
    }

    @Test
    void shouldNotValidateZeroDuration() {
        var sla = MaxDurationSLA.builder()
            .id("dur")
            .type(SLA.Type.MAX_DURATION)
            .behavior(SLA.Behavior.FAIL)
            .duration(Duration.ZERO)
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getMessage()).contains("must be positive");
    }

    @Test
    void shouldNotValidateFlowWithNegativeSLADuration() {
        String source = """
            id: bad_sla
            namespace: company.team
            sla:
              - id: dur
                type: MAX_DURATION
                duration: PT-1H
                behavior: FAIL
            tasks:
              - id: t
                type: io.kestra.plugin.core.log.Log
                message: hello
            """;

        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", List.of(new FlowSource(null, source)));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getConstraints()).contains("must be positive");
    }
}
