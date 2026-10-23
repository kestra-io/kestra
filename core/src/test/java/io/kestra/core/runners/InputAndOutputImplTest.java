package io.kestra.core.runners;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.Type;

import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InputAndOutputImplTest {
    @Test
    void optionalOutputWithNullValueIsKept() throws Exception {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        RunContext runContext = mock(RunContext.class);
        when(applicationContext.getBean(FlowInputOutput.class)).thenReturn(mock(FlowInputOutput.class));
        when(runContext.render(anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

        InputAndOutputImpl inputAndOutput = new InputAndOutputImpl(applicationContext, runContext);
        Output output = Output.builder()
            .id("optional_out")
            .value(null)
            .type(Type.STRING)
            .required(false)
            .build();

        Map<String, Object> rendered = inputAndOutput.renderOutputs(List.of(output));

        assertThat(rendered).containsKey("optional_out");
        assertThat(rendered.get("optional_out")).isNull();
    }
}
