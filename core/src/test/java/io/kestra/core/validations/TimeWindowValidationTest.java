package io.kestra.core.validations;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class TimeWindowValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldDefaultTimeWindow() {
        var sla = TimeWindow.builder().build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldValidateDailyDeadline() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now()).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateDailyDeadlineWhenMissingParam() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.DAILY_TIME_DEADLINE).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": Time window of type `DAILY_TIME_DEADLINE` must have a deadline.\n");
    }

    @Test
    void shouldNotValidateDailyDeadlineWhenInvalidParam() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now()).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": Time window of type `DAILY_TIME_DEADLINE` cannot have a window.\n");
    }

    @Test
    void shouldValidateDailyTimeWindow() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.DAILY_TIME_WINDOW).startTime(LocalTime.now()).endTime(LocalTime.now()).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateDailyTimeWindowWhenMissingParam() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.DAILY_TIME_WINDOW).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(2);
        assertThat(valid.get().getMessage()).contains(": Time window of type `DAILY_TIME_WINDOW` must have an end time.\n");
        assertThat(valid.get().getMessage()).contains(": Time window of type `DAILY_TIME_WINDOW` must have a start time.\n");
    }

    @Test
    void shouldNotValidateDailyTimeWindowWhenInvalidParam() {
        var sla = TimeWindow.builder()
            .type(TimeWindow.Type.DAILY_TIME_WINDOW)
            .startTime(LocalTime.now())
            .endTime(LocalTime.now())
            .window(Duration.ofHours(1))
            .deadline(LocalTime.now())
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(2);
        assertThat(valid.get().getMessage()).contains(": Time window of type `DAILY_TIME_WINDOW` cannot have a window.\n");
        assertThat(valid.get().getMessage()).contains(": Time window of type `DAILY_TIME_WINDOW` cannot have a deadline.\n");
    }

    @Test
    void shouldNotValidateDailyTimeWindowWhenStartTimeLooksLikeADeadline() {
        // Regression guard for kestra-io/kestra#18763: the DAILY_TIME_WINDOW branch used
        // to inspect getStartTime() while reporting "cannot have a deadline", so a valid
        // startTime+endTime+window combination (the startTime is required for this type!)
        // produced a spurious "cannot have a deadline" violation alongside the real
        // "cannot have a window" one.
        var sla = TimeWindow.builder()
            .type(TimeWindow.Type.DAILY_TIME_WINDOW)
            .startTime(LocalTime.now())
            .endTime(LocalTime.now())
            .window(Duration.ofHours(1))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).contains(": Time window of type `DAILY_TIME_WINDOW` cannot have a window.\n");
        assertThat(valid.get().getMessage()).doesNotContain("cannot have a deadline");
    }

    @Test
    void shouldValidateDurationWindow() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.DURATION_WINDOW).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateDurationWindowWhenInvalidParam() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.DURATION_WINDOW).deadline(LocalTime.now()).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": Time window of type `DURATION_WINDOW` cannot have a deadline.\n");
    }

    @Test
    void shouldValidateSlidingWindow() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.SLIDING_WINDOW).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateSlidingWindowWhenInvalidParam() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.SLIDING_WINDOW).deadline(LocalTime.now()).window(Duration.ofHours(1)).build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": Time window of type `SLIDING_WINDOW` cannot have a deadline.\n");
    }

    @Test
    void shouldValidateWhenTimezoneIsValid() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now()).timezone("Europe/Paris").build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateWhenTimezoneIsInvalid() {
        var sla = TimeWindow.builder().type(TimeWindow.Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now()).timezone("Not/AZone").build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(sla);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getMessage()).contains("is not a valid time-zone ID");
    }
}