package io.kestra.core.validations;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.models.validations.ModelValidator;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class ConstantRetryValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldValidateValidRetry() throws Exception {
        var retry = Constant.builder()
            .maxAttempts(3)
            .maxDuration(Duration.ofSeconds(10))
            .interval(Duration.ofSeconds(1))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(retry);
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateInvalidRetry() throws Exception {
        var retry = Constant.builder()
            .maxAttempts(3)
            .maxDuration(Duration.ofSeconds(1))
            .interval(Duration.ofSeconds(10))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(retry);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).contains("'interval' must be less than 'maxDuration'");
    }

    @Test
    void shouldRejectIntervalAboveTenYears() {
        // an interval beyond the 10-year @DurationMax cap
        var retry = Constant.builder()
            .interval(Duration.ofDays(3651))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(retry);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getMessage()).contains("must not exceed P3650D");
    }

    @Test
    void shouldAcceptIntervalAtTenYears() {
        var retry = Constant.builder()
            .interval(Duration.ofDays(3650))
            .build();

        assertThat(modelValidator.isValid(retry).isEmpty()).isTrue();
    }
}
