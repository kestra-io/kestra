package io.kestra.core.exceptions;

import java.io.Serial;
import java.time.Duration;

import org.apache.commons.lang3.time.DurationFormatUtils;

public class TimeoutExceededException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public TimeoutExceededException(Duration timeout, Exception e) {
        super("Timeout after " + DurationFormatUtils.formatDurationHMS(timeout.toMillis()), e);
    }

    public TimeoutExceededException(final Duration timeout) {
        super("Timeout after " + DurationFormatUtils.formatDurationHMS(timeout.toMillis()));
    }
}
