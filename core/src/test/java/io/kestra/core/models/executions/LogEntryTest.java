package io.kestra.core.models.executions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class LogEntryTest {

    @Test
    public void should_format_to_log_map(){
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

}
