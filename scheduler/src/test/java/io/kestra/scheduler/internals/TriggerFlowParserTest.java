package io.kestra.scheduler.internals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.runners.ProcessedFlow;
import io.kestra.core.services.FlowParsingService;
import io.kestra.plugin.core.trigger.Schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TriggerFlowParserTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriggerFlowParserTest.class);

    private static final String LEGACY_CONDITIONS_SOURCE = """
        id: trigger-flow-parser
        namespace: io.kestra.tests
        tasks:
          - id: log
            type: io.kestra.plugin.core.log.Log
            message: hello
        triggers:
          - id: schedule
            type: io.kestra.plugin.core.trigger.Schedule
            cron: "* * * * *"
            conditions:
              - type: io.kestra.plugin.core.condition.DayWeek
                dayOfWeek: MONDAY
        """;

    private final FlowWithSource flow = FlowWithSource.builder()
        .tenantId("main")
        .namespace("io.kestra.tests")
        .id("trigger-flow-parser")
        .triggers(List.of(Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").build()))
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
    void shouldThrowWithTheReasonWhenStoredFlowHasNoTriggersAndParsingFails() throws Exception {
        // Given a flow 2.0 could not deserialize: kept as FlowWithException, without its trigger definitions
        FlowWithSource stored = FlowWithException.from(flow, new FlowProcessingException("Invalid type: io.kestra.plugin.core.flow.ForEach"));
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(stored)).thenThrow(new FlowProcessingException("Invalid type: io.kestra.plugin.core.flow.ForEach"));

        // When / Then there is nothing to degrade to: the caller gets the reason to report against the trigger
        assertThatThrownBy(() -> TriggerFlowParser.parseForTrigger(flowParsingService, stored, LOGGER))
            .isInstanceOf(FlowProcessingException.class)
            .hasMessage("Invalid type: io.kestra.plugin.core.flow.ForEach");
    }

    /**
     * A 1.x flow whose Schedule still filters through the removed `conditions`: the repository could not
     * deserialize it, so the scheduler gets a {@link FlowWithException} carrying only the source. Re-parsing it
     * fails too, and with no trigger definitions to degrade to the caller is told why — the trigger must not be
     * scheduled, since without its conditions it would fire on every occurrence.
     */
    @Test
    void shouldThrowForAStoredFlowWhoseTriggerCarriesARemovedProperty() {
        // Given the flow as the repository hands it over: unparsable, kept without its trigger definitions
        FlowWithSource stored = FlowWithException.from(
            flow.toBuilder().source(LEGACY_CONDITIONS_SOURCE).build(),
            new FlowProcessingException("stored as unparsable")
        );
        assertThat(stored.getTriggers()).isNull();

        // When / Then the real parsing service refuses it, so the scheduler skips the trigger
        assertThatThrownBy(() -> TriggerFlowParser.parseForTrigger(new FlowParsingService(), stored, LOGGER))
            .isInstanceOf(FlowProcessingException.class)
            .hasMessageContaining("Unrecognized property \"conditions\" on trigger \"schedule\"")
            .hasMessageContaining("replaced by \"when\"");
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
