package io.kestra.core.utils;

import java.time.Clock;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

/**
 * The wall clock as a bean, so time-driven code (executor loops, schedule dates) reads
 * {@code clock.instant()} and tests can hand it a fixed or stepped clock instead.
 */
@Factory
public class ClockFactory {

    @Singleton
    public Clock clock() {
        return Clock.systemUTC();
    }
}
