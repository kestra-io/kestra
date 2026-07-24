package io.kestra.core.exceptions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotFoundExceptionTest {
    private enum TestEntity {
        FLOW
    }

    @Test
    void shouldReturnEmptyEntityWhenNotProvided() {
        // Given
        NotFoundException noArg = new NotFoundException();
        NotFoundException messageOnly = new NotFoundException("Not found.");

        // When / Then
        assertThat(noArg.entity()).isEmpty();
        assertThat(messageOnly.entity()).isEmpty();
        assertThat(messageOnly.getMessage()).isEqualTo("Not found.");
    }

    @Test
    void shouldReturnEntityNameWhenProvided() {
        // Given
        NotFoundException exception = new NotFoundException(TestEntity.FLOW, "Flow not found for id 'abc'.");

        // When / Then
        assertThat(exception.entity()).contains("FLOW");
        assertThat(exception.getMessage()).isEqualTo("Flow not found for id 'abc'.");
    }
}
