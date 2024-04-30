package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.runners.RunContext;

/**
 * Use io.kestra.core.models.tasks.runners.DefaultLogConsumer instead
 */
@Deprecated
public class DefaultLogConsumer extends io.kestra.core.models.tasks.runners.DefaultLogConsumer {
    public DefaultLogConsumer(RunContext runContext) {
        super(runContext);
    }
}
