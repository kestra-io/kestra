package io.kestra.core.validations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.triggers.TimeSLA;
import io.kestra.core.models.validations.ModelValidator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;

@KestraTest
class TimeTimeSLAValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldDefaultSLA() {
        var sla = TimeSLA.builder().build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(true));
    }

    @Test
    void shouldValidateDailyDeadline() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now()).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(true));
    }

    @Test
    void shouldNotValidateDailyDeadlineWhenMissingParam() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.DAILY_TIME_DEADLINE).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(false));
        assertThat(valid.get().getConstraintViolations(), hasSize(1));
        assertThat(valid.get().getMessage(), is(": SLA of type `DAILY_TIME_DEADLINE` must have a deadline.\n"));
    }

    @Test
    void shouldNotValidateDailyDeadlineWhenInvalidParam() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now()).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(false));
        assertThat(valid.get().getConstraintViolations(), hasSize(1));
        assertThat(valid.get().getMessage(), is(": SLA of type `DAILY_TIME_DEADLINE` cannot have a window.\n"));
    }

    @Test
    void shouldValidateDailyTimeWindow() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.DAILY_TIME_WINDOW).startTime(LocalTime.now()).endTime(LocalTime.now()).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(true));
    }

    @Test
    void shouldNotValidateDailyTimeWindowWhenMissingParam() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.DAILY_TIME_WINDOW).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(false));
        assertThat(valid.get().getConstraintViolations(), hasSize(2));
        assertThat(valid.get().getMessage(), containsString(": SLA of type `DAILY_TIME_WINDOW` must have an end time.\n"));
        assertThat(valid.get().getMessage(), containsString(": SLA of type `DAILY_TIME_WINDOW` must have a start time.\n"));
    }

    @Test
    void shouldNotValidateDailyTimeWindowWhenInvalidParam() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.DAILY_TIME_WINDOW).startTime(LocalTime.now()).endTime(LocalTime.now()).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(false));
        assertThat(valid.get().getConstraintViolations(), hasSize(2));
        assertThat(valid.get().getMessage(), containsString(": SLA of type `DAILY_TIME_WINDOW` cannot have a window.\n"));
        assertThat(valid.get().getMessage(), containsString(": SLA of type `DAILY_TIME_WINDOW` cannot have a deadline.\n"));
    }

    @Test
    void shouldValidateDurationWindow() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.DURATION_WINDOW).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(true));
    }

    @Test
    void shouldNotValidateDurationWindowWhenInvalidParam() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.DURATION_WINDOW).deadline(LocalTime.now()).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(false));
        assertThat(valid.get().getConstraintViolations(), hasSize(1));
        assertThat(valid.get().getMessage(), is(": SLA of type `DURATION_WINDOW` cannot have a deadline.\n"));
    }

    @Test
    void shouldValidateSlidingWindow() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.SLIDING_WINDOW).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(true));
    }

    @Test
    void shouldNotValidateSlidingWindowWhenInvalidParam() {
        var sla = TimeSLA.builder().type(TimeSLA.Type.SLIDING_WINDOW).deadline(LocalTime.now()).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty(), is(false));
        assertThat(valid.get().getConstraintViolations(), hasSize(1));
        assertThat(valid.get().getMessage(), is(": SLA of type `SLIDING_WINDOW` cannot have a deadline.\n"));
    }
}