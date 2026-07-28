package io.kestra.core.models.collectors;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.BoolInput;
import io.kestra.core.models.flows.input.FormInput;
import io.kestra.core.models.flows.input.IntInput;
import io.kestra.core.models.flows.input.ReusableInputsInput;
import io.kestra.core.models.flows.input.StringInput;

import static org.assertj.core.api.Assertions.assertThat;

class FlowUsageTest {

    @Test
    void shouldCountInputTypesRecursingIntoFormChildren() {
        // Given
        List<Input<?>> inputs = List.of(
            StringInput.builder().id("in").type(Type.STRING).build(),
            FormInput.builder().id("form").type(Type.FORM).inputs(
                List.of(
                    IntInput.builder().id("count").type(Type.INT).build(),
                    BoolInput.builder().id("flag").type(Type.BOOL).build()
                )
            ).build(),
            ReusableInputsInput.builder().id("ref").type(Type.REUSABLE_INPUTS).ref("infra").build()
        );

        // When
        Map<String, Long> inputTypeCount = FlowUsage.inputTypeCount(inputs);

        // Then
        assertThat(inputTypeCount).containsEntry("STRING", 1L);
        assertThat(inputTypeCount).containsEntry("FORM", 1L);
        assertThat(inputTypeCount).containsEntry("INT", 1L);
        assertThat(inputTypeCount).containsEntry("BOOL", 1L);
        assertThat(inputTypeCount).containsEntry("REUSABLE_INPUTS", 1L);
    }

    @Test
    void shouldReturnEmptyMapForNoInputs() {
        assertThat(FlowUsage.inputTypeCount(null)).isEmpty();
        assertThat(FlowUsage.inputTypeCount(List.of())).isEmpty();
    }

    @Test
    void shouldAggregateInputTypeCountAcrossFlows() {
        // Given
        Flow flowA = Flow.builder()
            .id("a")
            .namespace("io.kestra.unittest")
            .inputs(List.of(StringInput.builder().id("in").type(Type.STRING).build()))
            .build();
        Flow flowB = Flow.builder()
            .id("b")
            .namespace("io.kestra.unittest")
            .inputs(
                List.of(
                    StringInput.builder().id("in").type(Type.STRING).build(),
                    ReusableInputsInput.builder().id("ref").type(Type.REUSABLE_INPUTS).ref("infra").build()
                )
            )
            .build();
        // excluded from the census: the 'tutorial' namespace is filtered out by FlowUsage.of(...)
        Flow tutorialFlow = Flow.builder()
            .id("c")
            .namespace("tutorial")
            .inputs(List.of(ReusableInputsInput.builder().id("ref").type(Type.REUSABLE_INPUTS).ref("infra").build()))
            .build();

        // When
        FlowUsage flowUsage = FlowUsage.of(List.of(flowA, flowB, tutorialFlow));

        // Then
        assertThat(flowUsage.getInputTypeCount()).containsEntry("STRING", 2L);
        assertThat(flowUsage.getInputTypeCount()).containsEntry("REUSABLE_INPUTS", 1L);
    }
}
