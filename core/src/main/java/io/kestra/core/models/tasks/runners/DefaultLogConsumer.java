package io.kestra.core.models.tasks.runners;

import io.kestra.core.runners.RunContext;
import io.kestra.core.tasks.PluginUtilsService;

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
        outputs.putAll(PluginUtilsService.parseOut(line, runContext.logger(), runContext));

        if (isStdErr) {
            this.stdErrCount.incrementAndGet();
            runContext.logger().warn(line);
        } else {
            this.stdOutCount.incrementAndGet();
            runContext.logger().info(line);
        }
    }
}
