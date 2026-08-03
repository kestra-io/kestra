package io.kestra.scheduler.internals;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.services.FlowParsingService;

import static org.assertj.core.api.Assertions.assertThat;
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
    void shouldSkipFlowWhenBlockedByGovernance() throws FlowProcessingException {
        // Given
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(flow)).thenThrow(new FlowBlockedException("Blocked by governance policy"));

        // When / Then a blocked flow is skipped: its triggers must not run
        assertThat(TriggerFlowParser.parseOrSkip(flowParsingService, flow, LOGGER)).isNull();
    }

    @Test
    void shouldDegradeToStoredFlowWhenParsingFails() throws FlowProcessingException {
        // Given
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(flow)).thenThrow(new FlowProcessingException("invalid"));

        // When / Then a non-governance failure keeps the flow as stored so existing triggers keep evaluating
        assertThat(TriggerFlowParser.parseOrSkip(flowParsingService, flow, LOGGER)).isSameAs(flow);
    }

    @Test
    void shouldReturnParsedFlowWhenParsingSucceeds() throws FlowProcessingException {
        // Given
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        FlowWithSource parsed = flow.toBuilder().revision(2).build();
        when(flowParsingService.parseForRuntime(flow)).thenReturn(parsed);

        // When / Then
        assertThat(TriggerFlowParser.parseOrSkip(flowParsingService, flow, LOGGER)).isSameAs(parsed);
    }
}
