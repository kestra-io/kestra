package io.kestra.core.validations;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.models.validations.ModelValidator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@KestraTest
public class ExponentialRetryValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldValidateValidRetry() throws Exception {
        var retry = Exponential.builder()
            .maxAttempt(3)
            .maxDuration(Duration.ofSeconds(10))
            .interval(Duration.ofSeconds(1))
            .maxInterval(Duration.ofSeconds(3))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(retry);
        assertThat(valid.isEmpty(), is(true));
    }

    @Test
    void shouldNotValidateInvalidRetry() throws Exception {
        var retry = Exponential.builder()
            .maxAttempt(3)
            .maxDuration(Duration.ofSeconds(1))
            .interval(Duration.ofSeconds(2))
            .maxInterval(Duration.ofSeconds(3))
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(retry);
        assertThat(valid.isEmpty(), is(false));
        assertThat(valid.get().getConstraintViolations(), hasSize(1));
        assertThat(valid.get().getMessage(), is(": 'interval' must be less than 'maxDuration' but is PT2S\n"));

        retry = Exponential.builder()
            .maxAttempt(3)
            .maxDuration(Duration.ofSeconds(12))
            .interval(Duration.ofSeconds(3))
            .maxInterval(Duration.ofSeconds(2))
            .build();

        valid = modelValidator.isValid(retry);
        assertThat(valid.isEmpty(), is(false));
        assertThat(valid.get().getConstraintViolations(), hasSize(1));
        assertThat(valid.get().getMessage(), is(": 'interval' must be less than 'maxInterval' but is PT3S\n"));
    }
}
