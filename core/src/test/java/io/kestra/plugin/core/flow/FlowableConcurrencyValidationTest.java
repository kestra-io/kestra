package io.kestra.plugin.core.flow;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.property.Property;
import io.kestra.plugin.core.log.Log;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class FlowableConcurrencyValidationTest {
    @Inject
    private Validator validator;

    @Test
    void shouldRejectNegativeParallelConcurrent() {
        Parallel task = Parallel.builder()
            .id("p")
            .type(Parallel.class.getName())
            .concurrent(Property.ofValue(-1))
            .tasks(List.of(Log.builder().id("a").type(Log.class.getName()).message("hi").build()))
            .build();

        Set<? extends ConstraintViolation<?>> violations = validator.validate(task);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("greater than or equal to 0"));
    }

    @Test
    void shouldAcceptZeroParallelConcurrent() {
        Parallel task = Parallel.builder()
            .id("p")
            .type(Parallel.class.getName())
            .concurrent(Property.ofValue(0))
            .tasks(List.of(Log.builder().id("a").type(Log.class.getName()).message("hi").build()))
            .build();

        assertThat(validator.validate(task)).isEmpty();
    }

    @Test
    void shouldRejectNegativeLoopUntilMaxIterations() {
        LoopUntil task = LoopUntil.builder()
            .id("l")
            .type(LoopUntil.class.getName())
            .condition(Property.ofValue("{{ true }}"))
            .checkFrequency(LoopUntil.CheckFrequency.builder().maxIterations(Property.ofValue(-1)).build())
            .tasks(List.of(Log.builder().id("a").type(Log.class.getName()).message("hi").build()))
            .build();

        Set<? extends ConstraintViolation<?>> violations = validator.validate(task);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("must be greater than 0"));
    }
}
