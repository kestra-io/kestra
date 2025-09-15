package io.kestra.core.validations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.tasks.retrys.Random;
import io.kestra.core.models.validations.ModelValidator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class RandomRetryValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldValidateValidRetry() throws Exception {
        var retry = Random.builder()
            .maxAttempts(3)
            .maxDuration(Duration.ofSeconds(10))
            .minInterval(Duration.ofSeconds(1))
            .maxInterval(Duration.ofSeconds(3))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(retry);
        assertThat(valid.isEmpty()).isTrue();
    }

    @Test
    void shouldNotValidateInvalidRetry() throws Exception {
        var retry = Random.builder()
            .maxAttempts(3)
            .maxDuration(Duration.ofSeconds(1))
            .minInterval(Duration.ofSeconds(2))
            .maxInterval(Duration.ofSeconds(3))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(retry);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": 'minInterval' must be less than 'maxDuration' but is PT2S\n");

        retry = Random.builder()
            .maxAttempts(3)
            .maxDuration(Duration.ofSeconds(12))
            .minInterval(Duration.ofSeconds(3))
            .maxInterval(Duration.ofSeconds(2))
            .build();

        valid = modelValidator.isValid(retry);
        assertThat(valid.isEmpty()).isFalse();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).isEqualTo(": 'minInterval' must be less than 'maxInterval' but is PT3S\n");
    }
}
