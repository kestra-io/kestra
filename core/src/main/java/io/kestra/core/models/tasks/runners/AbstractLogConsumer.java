package io.kestra.core.models.tasks.runners;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Base class for script engine log consumer.
 * Used to retrieve the script logs and outputs.
 */
public abstract class AbstractLogConsumer implements BiConsumer<String, Boolean> {
    protected final AtomicInteger stdOutCount = new AtomicInteger();

    protected final AtomicInteger stdErrCount = new AtomicInteger();

    @Getter
    protected final Map<String, Object> outputs = new HashMap<>();

    public int getStdOutCount() {
        return this.stdOutCount.get();
    }

    public int getStdErrCount() {
        return this.stdErrCount.get();
    }
}
