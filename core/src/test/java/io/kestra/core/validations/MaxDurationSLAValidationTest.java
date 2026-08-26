package io.kestra.core.validations;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class MaxDurationSLAValidationTest {
    @Inject
    private ModelValidator modelValidator;

    private static MaxDurationSLA.MaxDurationSLABuilder<?, ?> sla(Duration duration) {
        return MaxDurationSLA.builder()
            .id("dur")
            .type(SLA.Type.MAX_DURATION)
            .behavior(SLA.Behavior.FAIL)
            .duration(duration);
    }

    @Test
    void shouldBeValidWithAPositiveDuration() {
        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla(Duration.ofHours(1)).build());

        assertThat(valid).isEmpty();
    }

    @Test
    void shouldNotBeValidWhenDurationIsNegative() {
        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla(Duration.ofHours(-1)).build());

        assertThat(valid).isPresent();
        assertThat(valid.get().getConstraintViolations())
            .anySatisfy(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("duration");
                assertThat(violation.getMessage()).contains("must be at least PT0S");
            });
    }

    @Test
    void shouldBeValidWithAZeroDuration() {
        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla(Duration.ZERO).build());

        assertThat(valid).isEmpty();
    }

    @Test
    void shouldStillRejectADurationAboveTheMaximum() {
        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla(Duration.ofDays(4000)).build());

        assertThat(valid).isPresent();
        assertThat(valid.get().getConstraintViolations())
            .anySatisfy(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("duration");
                assertThat(violation.getMessage()).contains("must not exceed");
            });
    }
}
