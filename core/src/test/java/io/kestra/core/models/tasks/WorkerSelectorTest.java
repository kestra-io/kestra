package io.kestra.core.models.tasks;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerSelectorTest {

    @Test
    void shouldExposeTagsAndFallback() {
        WorkerSelector req = new WorkerSelector(List.of("docker", "linux"), WorkerQueueFallback.WAIT);

        assertThat(req.tags()).containsExactly("docker", "linux");
        assertThat(req.fallback()).isEqualTo(WorkerQueueFallback.WAIT);
    }

    @Test
    void shouldDefineFourFallbackValues() {
        assertThat(WorkerQueueFallback.values())
            .containsExactlyInAnyOrder(
                WorkerQueueFallback.FAIL,
                WorkerQueueFallback.WAIT,
                WorkerQueueFallback.CANCEL,
                WorkerQueueFallback.IGNORE);
    }

    @Test
    void shouldAllowNullFallback() {
        WorkerSelector req = new WorkerSelector(List.of("docker"), null);
        assertThat(req.fallback()).isNull();
    }

    @Test
    void shouldRejectFallbackWithoutTags() {
        try (jakarta.validation.ValidatorFactory factory = validatorFactory()) {
            jakarta.validation.Validator validator = factory.getValidator();

            WorkerSelector emptyTags = new WorkerSelector(List.of(), WorkerQueueFallback.WAIT);
            WorkerSelector nullTags = new WorkerSelector(null, WorkerQueueFallback.WAIT);

            assertThat(validator.validate(emptyTags)).isNotEmpty();
            assertThat(validator.validate(nullTags)).isNotEmpty();
        }
    }

    @Test
    void shouldAcceptTagsWithoutFallback() {
        try (jakarta.validation.ValidatorFactory factory = validatorFactory()) {
            jakarta.validation.Validator validator = factory.getValidator();
            WorkerSelector ok = new WorkerSelector(List.of("docker"), null);
            assertThat(validator.validate(ok)).isEmpty();
        }
    }

    @Test
    void shouldExposeMatchField() {
        WorkerSelector req = new WorkerSelector(List.of("docker"), WorkerSelectorMatch.ANY, null);

        assertThat(req.match()).isEqualTo(WorkerSelectorMatch.ANY);
    }

    @Test
    void shouldRejectMatchWithoutTags() {
        try (jakarta.validation.ValidatorFactory factory = validatorFactory()) {
            jakarta.validation.Validator validator = factory.getValidator();

            WorkerSelector emptyTags = new WorkerSelector(List.of(), WorkerSelectorMatch.ANY, null);
            WorkerSelector nullTags = new WorkerSelector(null, WorkerSelectorMatch.ANY, null);

            assertThat(validator.validate(emptyTags)).isNotEmpty();
            assertThat(validator.validate(nullTags)).isNotEmpty();
        }
    }

    /**
     * Builds a validator factory configured with {@link org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator}
     * to avoid requiring the optional EL dependency on the core test classpath.
     */
    private static jakarta.validation.ValidatorFactory validatorFactory() {
        return jakarta.validation.Validation
            .byDefaultProvider()
            .configure()
            .messageInterpolator(new org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator())
            .buildValidatorFactory();
    }
}
