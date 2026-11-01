package io.kestra.core.models.validations;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.services.FlowParsingService;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class ViolationPathsTest {

    @Inject
    Validator validator;

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
    void shouldNameTheTaskByIdInTheFriendlyPath() throws FlowProcessingException {
        // Building the FlowWithSource for root bean
        String source = """
            id: nested_flow_test
            namespace: company.team
            tasks:
              - id: nested
                type: io.kestra.plugin.core.flow.Sequential
                tasks:
                  - id: my-task
                    type: io.kestra.plugin.core.log.Log
            """;
        var o = new FlowParsingService();
        FlowWithSource flowWithSource = o.parse("main", source, true);

        Set<ConstraintViolation<FlowWithSource>> violations = validator.validate(flowWithSource);
        assertThat(ViolationPaths.toFriendlyPath((ConstraintViolation<?>) violations.toArray()[0])).isEqualTo("tasks[nested].tasks[my-task].message");
    }

    @Test
    void shouldFallBackToTheRawPathWhenThereIsNoId() {
        // Given a violation on something that is not a task or an input
        var violation = ManualConstraintViolation.of("must not be blank", "value", String.class, "namespace", null);

        // Then the raw property path is used unchanged
        assertThat(ViolationPaths.toFriendlyPath(violation)).isEqualTo("namespace");
    }
}
