package io.kestra.core.models.tasks.logs;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.models.executions.LogEntry;
import java.time.Instant;
import org.assertj.core.api.BDDSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.event.Level;

@ExtendWith(SoftAssertionsExtension.class)
public class LogRecordMapperTest {

    @Test
    public void should_map_to_log_entry_to_log_record(BDDSoftAssertions softly){
        LogRecord logRecord = LogRecordMapper.mapToLogRecord(LogEntry.builder()
            .tenantId("tenantId")
            .namespace("namespace")
            .flowId("flowId")
            .taskId("taskId")
            .executionId("executionId")
            .taskRunId("taskRunId")
            .attemptNumber(1)
            .triggerId("triggerId")
            .timestamp(Instant.parse("2011-12-03T10:15:30.123456789Z"))
            .level(Level.INFO)
            .thread("thread")
            .message("message")
            .build());
        softly.then(logRecord.getResource()).isEqualTo("Kestra");
        softly.then(logRecord.getTimestampEpochNanos()).isEqualTo(1322907330123456789L);
        softly.then(logRecord.getSeverity()).isEqualTo("INFO");
        softly.then(logRecord.getAttributes().get("tenantId")).isEqualTo("tenantId");
        softly.then(logRecord.getAttributes().get("namespace")).isEqualTo("namespace");
        softly.then(logRecord.getAttributes().get("flowId")).isEqualTo("flowId");
        softly.then(logRecord.getAttributes().get("taskId")).isEqualTo("taskId");
        softly.then(logRecord.getAttributes().get("executionId")).isEqualTo("executionId");
        softly.then(logRecord.getAttributes().get("taskRunId")).isEqualTo("taskRunId");
        softly.then(logRecord.getAttributes().get("attemptNumber")).isEqualTo(1);
        softly.then(logRecord.getAttributes().get("triggerId")).isEqualTo("triggerId");
        softly.then(logRecord.getAttributes().get("thread")).isEqualTo("thread");
        softly.then(logRecord.getAttributes().get("message")).isEqualTo("message");
        softly.then(logRecord.getBodyValue()).isEqualTo("2011-12-03T10:15:30.123456789Z INFO message");
    }

    @Test
    public void should_convert_instant_in_nanos(){
        Instant instant = Instant.parse("2011-12-03T10:15:30.123456789Z");
        assertThat(LogRecordMapper.instantInNanos(instant)).isEqualTo(1322907330123456789L);
    }

}
