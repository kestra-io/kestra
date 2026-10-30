package io.kestra.core.validations;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.plugin.core.flow.Sleep;
import io.kestra.plugin.core.trigger.Schedule;

import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PositiveDurationValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Inject
    private Validator validator;

    @Test
    void shouldAcceptPositiveMaxDurationSla() {
        MaxDurationSLA sla = MaxDurationSLA.builder()
            .id("sla")
            .type(SLA.Type.MAX_DURATION)
            .behavior(SLA.Behavior.FAIL)
            .duration(Duration.ofMinutes(5))
            .build();

        assertThat(modelValidator.isValid(sla).isEmpty()).isTrue();
    }

    @Test
    void shouldRejectNegativeMaxDurationSla() {
        MaxDurationSLA sla = MaxDurationSLA.builder()
            .id("sla")
            .type(SLA.Type.MAX_DURATION)
            .behavior(SLA.Behavior.FAIL)
            .duration(Duration.ofHours(-1))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getMessage()).contains("must be a positive duration");
    }

    @Test
    void shouldRejectZeroMaxDurationSla() {
        MaxDurationSLA sla = MaxDurationSLA.builder()
            .id("sla")
            .type(SLA.Type.MAX_DURATION)
            .behavior(SLA.Behavior.FAIL)
            .duration(Duration.ZERO)
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getMessage()).contains("must be a positive duration");
    }

    @Test
    void shouldRejectNegativeScheduleLateMaximumDelay() {
        Schedule schedule = Schedule.builder()
            .id("schedule")
            .type(Schedule.class.getName())
            .cron("* * * * *")
            .lateMaximumDelay(Duration.ofMinutes(-5))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(schedule);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getMessage()).contains("must be a positive duration");
    }

    @Test
    void shouldAcceptPositiveSleepDurationProperty() {
        Sleep sleep = Sleep.builder()
            .id("sleep")
            .type(Sleep.class.getName())
            .duration(Property.ofValue(Duration.ofSeconds(5)))
            .build();

        assertThat(validator.validate(sleep)).isEmpty();
    }

    @Test
    void shouldRejectNegativeSleepDurationProperty() {
        Sleep sleep = Sleep.builder()
            .id("sleep")
            .type(Sleep.class.getName())
            .duration(Property.ofValue(Duration.ofSeconds(-5)))
            .build();

        Set<? extends ConstraintViolation<?>> violations = validator.validate(sleep);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("must be a positive duration"));
    }
}
