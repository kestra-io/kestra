package io.kestra.core.validations;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.plugin.core.trigger.Flow;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class FlowTriggerValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldBeValidWhenModeIsAllWithoutMinSatisfied() {
        // Given
        var trigger = Flow.builder()
            .id("flow-trigger")
            .type(Flow.class.getName())
            .mode(MultipleCondition.Mode.ALL)
            .build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(trigger);

        // Then
        assertThat(valid).isEmpty();
    }

    @Test
    void shouldBeValidWhenModeIsAnyWithoutMinSatisfied() {
        // Given
        var trigger = Flow.builder()
            .id("flow-trigger")
            .type(Flow.class.getName())
            .mode(MultipleCondition.Mode.ANY)
            .build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(trigger);

        // Then
        assertThat(valid).isEmpty();
    }

    @Test
    void shouldBeValidWhenModeIsAtLeastWithMinSatisfied() {
        // Given
        var trigger = Flow.builder()
            .id("flow-trigger")
            .type(Flow.class.getName())
            .mode(MultipleCondition.Mode.AT_LEAST)
            .minSatisfied(2)
            .build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(trigger);

        // Then
        assertThat(valid).isEmpty();
    }

    @Test
    void shouldNotBeValidWhenModeIsAtLeastWithoutMinSatisfied() {
        // Given
        var trigger = Flow.builder()
            .id("flow-trigger")
            .type(Flow.class.getName())
            .mode(MultipleCondition.Mode.AT_LEAST)
            .build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(trigger);

        // Then
        assertThat(valid).isPresent();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getMessage()).contains("`minSatisfied` must be set when mode is AT_LEAST");
    }

    @Test
    void shouldNotBeValidWhenMinSatisfiedIsNotPositive() {
        // Given
        var trigger = Flow.builder()
            .id("flow-trigger")
            .type(Flow.class.getName())
            .mode(MultipleCondition.Mode.AT_LEAST)
            .minSatisfied(0)
            .build();

        // When
        Optional<ConstraintViolationException> valid = modelValidator.isValid(trigger);

        // Then
        assertThat(valid).isPresent();
        assertThat(valid.get().getConstraintViolations()).hasSize(1);
        assertThat(valid.get().getConstraintViolations().iterator().next().getPropertyPath().toString()).isEqualTo("minSatisfied");
    }
}
