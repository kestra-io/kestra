package io.kestra.core.models.tasks.runners;

import java.time.Instant;

import io.kestra.core.runners.RunContext;

/**
 * Default implementation of an @{link {@link AbstractLogConsumer}}
 */
public class DefaultLogConsumer extends AbstractLogConsumer {
    private static final String LOG_DEBUG_MARKER = "##kestra:log:debug##";
    private static final String LOG_INFO_MARKER = "##kestra:log:info##";

    private final RunContext runContext;
    private final boolean forwardTraces;
    private volatile boolean debugMode = false;

    public DefaultLogConsumer(RunContext runContext) {
        this.runContext = runContext;
        this.forwardTraces = false;
    }

    public DefaultLogConsumer(RunContext runContext, boolean forwardTraces) {
        this.runContext = runContext;
        this.forwardTraces = forwardTraces;
    }

    @Override
    public void accept(String line, Boolean isStdErr) {
        this.accept(line, isStdErr, null);
    }

    public void accept(String line, Boolean isStdErr, Instant instant) {
        if (LOG_DEBUG_MARKER.equals(line)) {
            debugMode = true;
            return;
        }
        if (LOG_INFO_MARKER.equals(line)) {
            debugMode = false;
            return;
        }
        outputs.putAll(PluginUtilsService.parseOut(line, runContext.logger(), runContext, isStdErr, instant, debugMode, forwardTraces));

        if (isStdErr) {
            this.stdErrCount.incrementAndGet();
        } else {
            this.stdOutCount.incrementAndGet();
        }
    }
}
