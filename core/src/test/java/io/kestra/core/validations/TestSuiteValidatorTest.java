package io.kestra.core.validations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.services.FlowService;
import io.kestra.core.test.TestSuite;
import io.kestra.core.test.flow.Fixtures;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.test.flow.UnitTest;
import io.kestra.core.validations.validator.TestSuiteValidator;
import io.kestra.plugin.core.log.Log;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestSuiteValidatorTest {
    @Mock
    private FlowService flowService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ConstraintValidatorContext context;

    @InjectMocks
    private TestSuiteValidator validator;

    @Test
    void shouldBeValidWhenFixturesReferenceExistingInputsAndTasks() {
        // Given
        givenFlow();
        TestSuite testSuite = testSuite(Fixtures.builder()
            .inputs(Map.of("name", "value"))
            .tasks(List.of(TaskFixture.builder().id("log").build()))
            .build());

        // When
        boolean valid = isValid(testSuite);

        // Then
        assertThat(valid).isTrue();
        verify(context, never()).disableDefaultConstraintViolation();
    }

    @Test
    void shouldBeInvalidWhenFixtureInputDoesNotExistInFlow() {
        // Given
        givenFlow();
        TestSuite testSuite = testSuite(Fixtures.builder().inputs(Map.of("unknown", "value")).build());

        // When
        boolean valid = isValid(testSuite);

        // Then
        assertThat(valid).isFalse();
        verify(context).buildConstraintViolationWithTemplate(contains("Input 'unknown' does not exist in the flow"));
    }

    @Test
    void shouldBeInvalidWhenFixtureTaskDoesNotExistInFlow() {
        // Given
        givenFlow();
        TestSuite testSuite = testSuite(Fixtures.builder()
            .tasks(List.of(TaskFixture.builder().id("unknown").build()))
            .build());

        // When
        boolean valid = isValid(testSuite);

        // Then
        assertThat(valid).isFalse();
        verify(context).buildConstraintViolationWithTemplate(contains("Task 'unknown' does not exist in the flow"));
    }

    @Test
    void shouldBeInvalidWhenFlowDoesNotExist() {
        // Given
        when(flowService.findById("main", "io.kestra.tests", "my-flow")).thenReturn(Optional.empty());
        TestSuite testSuite = testSuite(Fixtures.builder().build());

        // When
        boolean valid = isValid(testSuite);

        // Then
        assertThat(valid).isFalse();
        verify(context).buildConstraintViolationWithTemplate(contains("Flow with id: 'my-flow' does not exist"));
    }

    private void givenFlow() {
        Flow flow = Flow.builder()
            .id("my-flow")
            .namespace("io.kestra.tests")
            .inputs(List.of(StringInput.builder().id("name").build()))
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message(Property.ofValue("hi")).build()))
            .build();
        when(flowService.findById("main", "io.kestra.tests", "my-flow")).thenReturn(Optional.of(flow));
    }

    private TestSuite testSuite(Fixtures fixtures) {
        return TestSuite.builder()
            .id("suite")
            .tenantId("main")
            .namespace("io.kestra.tests")
            .flowId("my-flow")
            .testCases(List.of(UnitTest.builder()
                .id("case")
                .type(UnitTest.class.getName())
                .fixtures(fixtures)
                .build()))
            .build();
    }

    private boolean isValid(TestSuite testSuite) {
        return validator.isValid(testSuite, AnnotationValue.builder(TestSuiteValidation.class).build(), context);
    }
}
