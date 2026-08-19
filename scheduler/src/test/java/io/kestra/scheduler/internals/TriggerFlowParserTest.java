package io.kestra.scheduler.internals;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.runners.ProcessedFlow;
import io.kestra.core.services.FlowParsingService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TriggerFlowParserTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerFlowParserTest.class);

    private final FlowWithSource flow = FlowWithSource.builder()
        .tenantId("main")
        .namespace("io.kestra.tests")
        .id("trigger-flow-parser")
        .build();

    @Test
    void shouldThrowWithTheReasonWhenBlockedByGovernance() throws FlowProcessingException {
        // Given
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(flow)).thenThrow(new FlowBlockedException("Blocked by governance policy: policy=deny-log"));

        // When / Then the block reaches the caller with its reason, so it can report it against the trigger
        assertThatThrownBy(() -> TriggerFlowParser.parseForTrigger(flowParsingService, flow, LOGGER))
            .isInstanceOf(FlowBlockedException.class)
            .hasMessage("Blocked by governance policy: policy=deny-log");
    }

    @Test
    void shouldDegradeToStoredFlowWhenParsingFails() throws Exception {
        // Given
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(flow)).thenThrow(new FlowProcessingException("invalid"));

        // When / Then a non-governance failure keeps the flow as stored so existing triggers keep evaluating
        assertThat(TriggerFlowParser.parseForTrigger(flowParsingService, flow, LOGGER)).isSameAs(flow);
    }

    @Test
    void shouldReturnParsedFlowWhenParsingSucceeds() throws Exception {
        // Given
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        FlowWithSource parsed = flow.toBuilder().revision(2).build();
        when(flowParsingService.parseForRuntime(flow)).thenReturn(ProcessedFlow.of(parsed));

        // When / Then
        assertThat(TriggerFlowParser.parseForTrigger(flowParsingService, flow, LOGGER)).isSameAs(parsed);
    }
}
