package io.kestra.core.test.flow;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.validations.ModelValidator;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class TerminalStateValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void shouldRejectUnitTestExpectingUnfinishedState() {
        UnitTest unitTest = UnitTest.builder().id("case").type(UnitTest.class.getName()).expectedState(State.Type.PAUSED).build();

        assertThat(modelValidator.isValid(unitTest)).get().satisfies(e -> assertThat(e.getMessage()).contains("The expected state should be a terminal state."));
    }

    @Test
    void shouldRejectTaskFixtureWithUnfinishedState() {
        TaskFixture fixture = TaskFixture.builder().id("log").state(State.Type.PAUSED).build();

        assertThat(modelValidator.isValid(fixture)).get().satisfies(e -> assertThat(e.getMessage()).contains("The state should be a terminal state."));
    }

    @Test
    void shouldAcceptTerminalStates() {
        UnitTest unitTest = UnitTest.builder().id("case").type(UnitTest.class.getName()).assertions(List.of()).expectedState(State.Type.FAILED).build();
        TaskFixture fixture = TaskFixture.builder().id("log").state(State.Type.WARNING).build();

        assertThat(modelValidator.isValid(unitTest)).isEmpty();
        assertThat(modelValidator.isValid(fixture)).isEmpty();
    }
}
