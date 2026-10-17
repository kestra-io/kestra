package io.kestra.executor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a flow trigger misconfiguration be logged on the flow that owns the trigger at most once per window: a Flow
 * trigger is evaluated against every upstream execution, so logging each failed evaluation would flood that flow's logs.
 */
class FlowTriggerErrorLogThrottle {
    private final Duration window;
    private final Clock clock;
    private final Map<String, Instant> lastLogged = new ConcurrentHashMap<>();

    FlowTriggerErrorLogThrottle(Duration window, Clock clock) {
        this.window = window;
        this.clock = clock;
    }

    Duration window() {
        return window;
    }

    boolean shouldLog(String key) {
        Instant now = clock.instant();
        lastLogged.values().removeIf(loggedAt -> !loggedAt.plus(window).isAfter(now));
        return lastLogged.putIfAbsent(key, now) == null;
    }
}
