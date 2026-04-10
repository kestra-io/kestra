package io.kestra.core.validations;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.triggers.Window;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class WindowValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldDefaultWindow() {
        // Given
        var window = Window.builder().build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldValidateDailyTimeDeadline() {
        // Given
        var window = Window.builder().deadline(LocalTime.now()).build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateDailyTimeDeadlineWhenInvalidParam() {
        // Given
        var window = Window.builder().deadline(LocalTime.now()).every(Duration.ofHours(1)).build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": Window of type `DAILY_TIME_DEADLINE` cannot have an every duration.\n");
    }

    @Test
    void shouldValidateDailyTimeWindow() {
        // Given
        var window = Window.builder().from(LocalTime.of(8, 0)).to(LocalTime.of(18, 0)).build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateDailyTimeWindowWhenMissingTo() {
        // Given
        var window = Window.builder().from(LocalTime.of(8, 0)).build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": Window of type `DAILY_TIME_WINDOW` must have a to time.\n");
    }

    @Test
    void shouldNotValidateDailyTimeWindowWhenMissingFrom() {
        // Given
        var window = Window.builder().to(LocalTime.of(18, 0)).build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": Window of type `DAILY_TIME_WINDOW` must have a from time.\n");
    }

    @Test
    void shouldNotValidateDailyTimeWindowWhenInvalidParam() {
        // Given
        var window = Window.builder().from(LocalTime.of(8, 0)).to(LocalTime.of(18, 0)).every(Duration.ofHours(1)).build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": Window of type `DAILY_TIME_WINDOW` cannot have an every duration.\n");
    }

    @Test
    void shouldValidateSlidingWindow() {
        // Given
        var window = Window.builder().lookback(Duration.ofHours(1)).build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateSlidingWindowWhenInvalidParam() {
        // Given
        var window = Window.builder().lookback(Duration.ofHours(1)).offset(Duration.ofMinutes(30)).build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": Window of type `SLIDING_WINDOW` cannot have an offset.\n");
    }

    @Test
    void shouldValidateDurationWindow() {
        // Given
        var window = Window.builder().every(Duration.ofHours(1)).build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(window);

        // Then
        assertThat(valid.isEmpty()).isTrue();
    }
}
