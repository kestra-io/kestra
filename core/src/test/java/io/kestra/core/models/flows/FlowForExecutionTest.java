package io.kestra.core.models.flows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

class FlowForExecutionTest {
    private static Flow.FlowBuilder<?, ?> flow() {
        return Flow.builder()
            .id("flow")
            .namespace("io.kestra.tests")
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("hello").build()));
    }

    @Test
    void shouldExposeOutputDeclarationsWithoutTheirValue() {
        // Given
        Flow flow = flow()
            .outputs(List.of(
                Output.builder().id("orderIds").type(OutputType.STRING).value("{{ outputs.log.secret }}").searchable(true).required(false).description("Orders").build(),
                Output.builder().id("plain").type(OutputType.INT).value(42).build()
            ))
            .build();

        // When
        FlowForExecution forExecution = FlowForExecution.of(flow);

        // Then
        assertThat(forExecution.getOutputs()).hasSize(2).allSatisfy(output -> assertThat(output).isInstanceOf(OutputForExecution.class));
        Output orderIds = forExecution.getOutputs().getFirst();
        assertThat(orderIds.getId()).isEqualTo("orderIds");
        assertThat(orderIds.getType()).isEqualTo(OutputType.STRING);
        assertThat(orderIds.getSearchable()).isTrue();
        assertThat(orderIds.getRequired()).isFalse();
        assertThat(orderIds.getDescription()).isEqualTo("Orders");
        assertThat(forExecution.getOutputs()).allSatisfy(output -> assertThat(output.getValue()).isNull());
    }

    @Test
    void shouldExposeNoOutputWhenTheFlowHasNone() {
        assertThat(FlowForExecution.of(flow().build()).getOutputs()).isEmpty();
    }
}
