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
        assertThat(logMap).containsEntry("tenantId", "tenantId");
        assertThat(logMap).containsEntry("namespace", "namespace");
        assertThat(logMap).containsEntry("flowId", "flowId");
        assertThat(logMap).containsEntry("taskId", "taskId");
        assertThat(logMap).containsEntry("executionId", "executionId");
        assertThat(logMap).containsEntry("taskRunId", "taskRunId");
        assertThat(logMap).containsEntry("attemptNumber", 1);
        assertThat(logMap).containsEntry("triggerId", "triggerId");
        assertThat(logMap).containsEntry("thread", "thread");
        assertThat(logMap).containsEntry("message", "message");
    }

}
