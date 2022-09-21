package io.kestra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZonedDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

class JdbcMapperTest {
    @Test
    void instant() throws  JsonProcessingException {
        String serialize = JdbcMapper.of().writeValueAsString(LogEntry.builder()
            .timestamp(Instant.parse("2019-10-06T18:27:49.000Z"))
            .build()
        );

        assertThat(serialize, containsString("2019-10-06T18:27:49.000Z"));
    }

    @Test
    void zoneDateTime() throws  JsonProcessingException {
        String serialize = JdbcMapper.of().writeValueAsString(MultipleConditionWindow.builder()
            .start(ZonedDateTime.parse("2013-09-08T16:19:12.000000+02:00"))
            .build()
        );

        assertThat(serialize, containsString("2013-09-08T16:19:12.000+02:00"));
    }

    @Test
    void zoneDateTimeMs() throws  JsonProcessingException {
        String serialize = JdbcMapper.of().writeValueAsString(MultipleConditionWindow.builder()
            .start(ZonedDateTime.parse("2013-09-08T16:19:12.001234+02:00"))
            .build()
        );

        assertThat(serialize, containsString("2013-09-08T16:19:12.001+02:00"));
    }
}