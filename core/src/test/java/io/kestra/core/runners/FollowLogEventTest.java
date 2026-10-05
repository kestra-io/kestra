package io.kestra.core.runners;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class FollowLogEventTest {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Test
    void shouldMapFromLogEntryWithoutTenantIdOrThread() {
        LogEntry logEntry = LogEntry.builder()
            .tenantId("tenant-123")
            .namespace("io.kestra.unit")
            .flowId("test-flow")
            .taskId("task-1")
            .executionId("exec-abc")
            .taskRunId("tr-xyz")
            .attemptNumber(1)
            .triggerId("trig-1")
            .timestamp(Instant.parse("2026-10-05T10:00:00Z"))
            .level(Level.INFO)
            .thread("worker-thread-4")
            .message("Processing finished")
            .executionKind(ExecutionKind.NORMAL)
            .progress("50%")
            .build();

        FollowLogEvent event = FollowLogEvent.from(logEntry);

        assertThat(event.namespace()).isEqualTo("io.kestra.unit");
        assertThat(event.flowId()).isEqualTo("test-flow");
        assertThat(event.taskId()).isEqualTo("task-1");
        assertThat(event.executionId()).isEqualTo("exec-abc");
        assertThat(event.taskRunId()).isEqualTo("tr-xyz");
        assertThat(event.attemptNumber()).isEqualTo(1);
        assertThat(event.triggerId()).isEqualTo("trig-1");
        assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-10-05T10:00:00Z"));
        assertThat(event.level()).isEqualTo(Level.INFO);
        assertThat(event.message()).isEqualTo("Processing finished");
        assertThat(event.executionKind()).isEqualTo(ExecutionKind.NORMAL);
        assertThat(event.progress()).isEqualTo("50%");
    }

    @Test
    void shouldOmitNullFieldsAndOmitTenantIdAndThreadWhenSerialized() throws JsonProcessingException {
        FollowLogEvent event = new FollowLogEvent(
            "io.kestra.unit",
            "test-flow",
            "task-1",
            "exec-abc",
            "tr-xyz",
            0,
            null,
            Instant.parse("2026-10-05T10:00:00Z"),
            Level.INFO,
            "A log line",
            null,
            null
        );

        String json = MAPPER.writeValueAsString(event);
        JsonNode node = MAPPER.readTree(json);

        assertThat(node.has("namespace")).isTrue();
        assertThat(node.has("flowId")).isTrue();
        assertThat(node.has("taskId")).isTrue();
        assertThat(node.has("executionId")).isTrue();
        assertThat(node.has("taskRunId")).isTrue();
        assertThat(node.has("attemptNumber")).isTrue();
        assertThat(node.has("timestamp")).isTrue();
        assertThat(node.has("level")).isTrue();
        assertThat(node.has("message")).isTrue();

        assertThat(node.has("tenantId")).isFalse();
        assertThat(node.has("thread")).isFalse();
        assertThat(node.has("triggerId")).isFalse();
        assertThat(node.has("executionKind")).isFalse();
        assertThat(node.has("progress")).isFalse();

        FollowLogEvent deserialized = MAPPER.readValue(json, FollowLogEvent.class);
        assertThat(deserialized).isEqualTo(event);
    }
}

