package io.kestra.core.models.validations;


import org.junit.jupiter.api.Test;

import io.kestra.core.models.tasks.Task;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

class ViolationPathsTest {
    @Test
    void shouldBuildAJsonPointerFromAnIndexedPath() {
        // Given a violation on an element of a collection
        var violation = ManualConstraintViolation.of("must not be null", "value", String.class, "tasks[0].type", null);

        // Then the pointer addresses it per RFC 6901
        assertThat(ViolationPaths.toJsonPointer(violation.getPropertyPath())).isEqualTo("/tasks/0/type");
    }

    @Test
    void shouldEscapeReservedCharactersInAPointer() {
        // Given a property name containing the two characters RFC 6901 reserves
        var violation = ManualConstraintViolation.of("invalid", "value", String.class, "labels[a/b~c]", null);

        // Then both are escaped, in the order the RFC requires
        assertThat(ViolationPaths.toJsonPointer(violation.getPropertyPath())).contains("a~1b~0c");
    }

    @Test
    void shouldNameTheTaskByIdInTheFriendlyPath() {
        // Given a violation whose leaf bean is a task
        Task task = Log.builder().id("my-task").type(Log.class.getName()).build();
        var violation = ManualConstraintViolation.of("must not be null", task, Task.class, "tasks[0].type", null);

        // Then the friendly path names the task rather than its index, keeping the collection name
        assertThat(ViolationPaths.toFriendlyPath(violation)).isEqualTo("tasks[my-task].type");
    }

    @Test
    void shouldFallBackToTheRawPathWhenThereIsNoId() {
        // Given a violation on something that is not a task or an input
        var violation = ManualConstraintViolation.of("must not be blank", "value", String.class, "namespace", null);

        // Then the raw property path is used unchanged
        assertThat(ViolationPaths.toFriendlyPath(violation)).isEqualTo("namespace");
    }
}
