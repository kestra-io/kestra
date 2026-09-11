package io.kestra.webserver.errors;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemErrorTest {
    @Test
    void shouldCarryBothLocatorsOnEveryError() {
        // Given a violation on a task's property
        Task task = Log.builder().id("my-task").type(Log.class.getName()).build();
        var violations = Set.of(
            ManualConstraintViolation.of("must not be null", task, Task.class, "tasks[0].type", null)
        );

        // When it is converted
        var errors = ProblemError.ofViolations(violations);

        // Then each error carries the machine locator and the human one, which are deliberately different
        assertThat(errors).singleElement().satisfies(error -> {
            assertThat(error.detail()).isEqualTo("must not be null");
            assertThat(error.pointer()).isEqualTo("/tasks/0/type");
            assertThat(error.path()).isEqualTo("tasks[my-task].type");
        });
    }

    @Test
    void shouldOrderErrorsStablyGivenAnUnorderedSet() {
        // Given violations arriving from a Set, whose iteration order is not stable
        var first = ManualConstraintViolation.of("a", "v", String.class, "alpha", null);
        var second = ManualConstraintViolation.of("b", "v", String.class, "beta", null);

        // Then conversion sorts them, so the same input always produces the same document
        assertThat(ProblemError.ofViolations(Set.of(first, second)).stream().map(ProblemError::pointer))
            .containsExactly("/alpha", "/beta");
    }
}
