package io.kestra.core.models.tasks.runners;

import io.kestra.core.runners.RunContext;

import java.sql.Timestamp;

/**
 * Default implementation of an @{link {@link AbstractLogConsumer}}
 */
public class DefaultLogConsumer extends AbstractLogConsumer {
    private final RunContext runContext;

    public DefaultLogConsumer(RunContext runContext) {
        this.runContext = runContext;
    }

    @Override
    public void accept(String line, Boolean isStdErr) {
        this.accept(line, isStdErr, null);
    }

    public void accept(String line, Boolean isStdErr, Timestamp timestamp) {
        outputs.putAll(PluginUtilsService.parseOut(line, runContext.logger(), runContext, isStdErr, timestamp));

        if (isStdErr) {
            this.stdErrCount.incrementAndGet();
        } else {
            this.stdOutCount.incrementAndGet();
        }
    }
}
