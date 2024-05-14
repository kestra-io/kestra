package io.kestra.core.exceptions;

import org.apache.commons.lang3.time.DurationFormatUtils;

import java.time.Duration;

public class TimeoutExceededException extends Exception {
    private static final long serialVersionUID = 1L;

    public TimeoutExceededException(Duration timeout, Exception e) {
        super("Timeout after " + DurationFormatUtils.formatDurationHMS(timeout.toMillis()), e);
    }

    public TimeoutExceededException(final Duration timeout) {
        super("Timeout after " + DurationFormatUtils.formatDurationHMS(timeout.toMillis()));
    }
}
