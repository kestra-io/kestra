package io.kestra.core.validations;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class DurationMinValidationTest {
    @Inject
    private Validator validator;

    @Test
    void shouldAcceptNullOrDurationAboveDefaultMinimum() {
        assertThat(validator.validate(new Bounded(null))).isEmpty();
        assertThat(validator.validate(new Bounded(Duration.ofMillis(1)))).isEmpty();
        assertThat(validator.validate(new Bounded(Duration.ofMinutes(10)))).isEmpty();
    }

    @Test
    void shouldRejectZeroOrNegativeGivenDefaultMinimum() {
        assertThat(validator.validate(new Bounded(Duration.ZERO)))
            .anyMatch(v -> v.getMessage().contains("must be at least PT0.001S"));
        assertThat(validator.validate(new Bounded(Duration.ofMinutes(-10))))
            .anyMatch(v -> v.getMessage().contains("must be at least PT0.001S"));
    }

    /**
     * An explicit {@code min} reaches the validator only through constructor validation, which is the
     * path an HTTP request body takes. Hibernate-delegated validation drops annotation members.
     */
    @Test
    void shouldHonourExplicitMinimumWhenValidatingAConstructor() {
        BeanIntrospection<AtLeastOneMinute> introspection = BeanIntrospection.getIntrospection(AtLeastOneMinute.class);

        assertThat(
            validator.forExecutables()
                .validateConstructorParameters(introspection, new Object[] { Duration.ofMinutes(1) }, new Class[0])
        )
            .isEmpty();
        assertThat(
            validator.forExecutables()
                .validateConstructorParameters(introspection, new Object[] { Duration.ofSeconds(59) }, new Class[0])
        )
            .anyMatch(v -> v.getMessage().contains("must be at least PT1M"));
    }

    @Introspected
    record Bounded(@DurationMin Duration duration) {
    }

    @Introspected
    record AtLeastOneMinute(@DurationMin(min = "PT1M") Duration duration) {
    }
}
