package io.kestra.core.models.executions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
        assertThat(logMap.get("tenantId"), is("tenantId"));
        assertThat(logMap.get("namespace"), is("namespace"));
        assertThat(logMap.get("flowId"), is("flowId"));
        assertThat(logMap.get("taskId"), is("taskId"));
        assertThat(logMap.get("executionId"), is("executionId"));
        assertThat(logMap.get("taskRunId"), is("taskRunId"));
        assertThat(logMap.get("attemptNumber"), is(1));
        assertThat(logMap.get("triggerId"), is("triggerId"));
        assertThat(logMap.get("thread"), is("thread"));
        assertThat(logMap.get("message"), is("message"));
    }

}
