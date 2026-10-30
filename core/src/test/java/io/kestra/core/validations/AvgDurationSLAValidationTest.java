package io.kestra.core.validations;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.AvgDurationSLA;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class AvgDurationSLAValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldAcceptSinglePositiveThreshold() {
        assertThat(modelValidator.isValid(sla().percentageThreshold(0.2f).build())).isEmpty();
        assertThat(modelValidator.isValid(sla().durationThreshold(Duration.ofSeconds(5)).build())).isEmpty();
    }

    @Test
    void shouldRejectBothThresholds() {
        Optional<ConstraintViolationException> valid = modelValidator.isValid(
            sla().percentageThreshold(0.2f).durationThreshold(Duration.ofSeconds(5)).build()
        );

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).contains("durationThreshold and percentageThreshold cannot both be set");
    }

    @Test
    void shouldRejectNonPositivePercentageThreshold() {
        assertThat(modelValidator.isValid(sla().percentageThreshold(0f).build())).isPresent();
        assertThat(modelValidator.isValid(sla().percentageThreshold(-0.5f).build())).isPresent();
    }

    @Test
    void shouldRejectNonPositiveDurationThreshold() {
        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla().durationThreshold(Duration.ofSeconds(-5)).build());

        assertThat(valid).isPresent();
        assertThat(valid.get().getMessage()).contains("must be a positive duration");
        assertThat(modelValidator.isValid(sla().durationThreshold(Duration.ZERO).build())).isPresent();
    }

    @Test
    void shouldRejectMinExecutionsBelowOne() {
        assertThat(modelValidator.isValid(sla().minExecutions(0).build())).isPresent();
    }

    private static AvgDurationSLA.AvgDurationSLABuilder<?, ?> sla() {
        return AvgDurationSLA.builder()
            .id("sla")
            .type(SLA.Type.AVG_DURATION)
            .behavior(SLA.Behavior.FAIL)
            .lookbackPeriod(Duration.ofDays(7));
    }
}
