package io.kestra.core.utils;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateUtilsTest {

    @Test
    void shouldAddDurationToInstantNormally() throws Exception {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        assertThat(DateUtils.plusOrThrow(now, Duration.ofHours(2)))
            .isEqualTo(Instant.parse("2025-01-01T02:00:00Z"));
    }

    @Test
    void shouldThrowInternalExceptionWhenInstantOverflows() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        // a duration large enough to overflow Instant.plus
        assertThatThrownBy(() -> DateUtils.plusOrThrow(now, Duration.ofHours(9_000_000_000_000L)))
            .isInstanceOf(InternalException.class)
            .hasMessageContaining("out of the supported range");
    }
}
