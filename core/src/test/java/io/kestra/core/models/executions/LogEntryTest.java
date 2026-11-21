package io.kestra.core.models.executions;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.plugin.core.trigger.Schedule;

import static org.assertj.core.api.Assertions.assertThat;

public class LogEntryTest {

    @Test
    public void should_format_to_log_map() {
        LogEntry logEntry = LogEntry.builder()
            .tenantId("tenantId")
            .namespace("namespace")
            .flowId("flowId")
            .taskId("taskId")
            .executionId("executionId")
            .taskRunId("taskRunId")
            .attemptNumber(1)
            .triggerId("triggerId")
            .thread("thread")
            .message("message")
            .build();
        Map<String, Object> logMap = logEntry.toLogMap();
        assertThat(logMap.get("tenantId")).isEqualTo("tenantId");
        assertThat(logMap.get("namespace")).isEqualTo("namespace");
        assertThat(logMap.get("flowId")).isEqualTo("flowId");
        assertThat(logMap.get("taskId")).isEqualTo("taskId");
        assertThat(logMap.get("executionId")).isEqualTo("executionId");
        assertThat(logMap.get("taskRunId")).isEqualTo("taskRunId");
        assertThat(logMap.get("attemptNumber")).isEqualTo(1);
        assertThat(logMap.get("triggerId")).isEqualTo("triggerId");
        assertThat(logMap.get("thread")).isEqualTo("thread");
        assertThat(logMap.get("message")).isEqualTo("message");
    }

    @Test
    public void should_not_set_execution_id_for_trigger_log_entry_from_trigger_id() {
        Schedule trigger = Schedule.builder().id("my_trigger").type(Schedule.class.getName()).cron("* * * * *").build();
        TriggerId triggerId = TriggerId.of("tenantId", "namespace", "flowId", "my_trigger");

        LogEntry logEntry = LogEntry.of(triggerId, trigger);

        assertThat(logEntry.getTriggerId()).isEqualTo("my_trigger");
        assertThat(logEntry.getExecutionId()).isNull();
        assertThat(logEntry.toMap()).doesNotContainKey("executionId");
    }

    @Test
    public void should_not_set_execution_id_for_trigger_log_entry_from_flow_id() {
        Schedule trigger = Schedule.builder().id("my_trigger").type(Schedule.class.getName()).cron("* * * * *").build();
        FlowId flowId = FlowId.of("tenantId", "namespace", "flowId", 1);

        LogEntry logEntry = LogEntry.of(flowId, trigger);

        assertThat(logEntry.getTriggerId()).isEqualTo("my_trigger");
        assertThat(logEntry.getExecutionId()).isNull();
        assertThat(logEntry.toMap()).doesNotContainKey("executionId");
    }

}

