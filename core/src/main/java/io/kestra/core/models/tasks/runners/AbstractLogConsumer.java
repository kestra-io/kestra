package io.kestra.core.models.tasks.runners;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import lombok.Getter;

/**
 * Base class for script engine log consumer.
 * Used to retrieve the script logs and outputs.
 */
public abstract class AbstractLogConsumer implements BiConsumer<String, Boolean> {
    protected final AtomicInteger stdOutCount = new AtomicInteger();

    protected final AtomicInteger stdErrCount = new AtomicInteger();

    @Getter
    protected final Map<String, Object> outputs = new HashMap<>();

    @Getter
    protected final Set<String> outputFiles = Collections.synchronizedSet(new HashSet<>());

    public abstract void accept(String line, Boolean isStdErr, Instant instant);

    public int getStdOutCount() {
        return this.stdOutCount.get();
    }

    public int getStdErrCount() {
        return this.stdErrCount.get();
    }
}
