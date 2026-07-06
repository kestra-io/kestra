package io.kestra.executor.testkit;

import java.util.List;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.utils.IdUtils;

/**
 * Builders-first flow fixtures for executor unit tests.
 */
public final class Flows {
    public static final String TENANT = "main";
    public static final String NAMESPACE = "io.kestra.tests";

    private Flows() {
        // utility class pattern
    }

    public static FlowWithSource of(Task... tasks) {
        return of(builder(tasks).build());
    }

    public static FlowWithSource withConcurrency(Concurrency concurrency, Task... tasks) {
        return of(builder(tasks).concurrency(concurrency).build());
    }

    public static Flow.FlowBuilder<?, ?> builder(Task... tasks) {
        return Flow.builder()
            .tenantId(TENANT)
            .namespace(NAMESPACE)
            .id("test-" + IdUtils.create())
            .revision(1)
            .tasks(List.of(tasks));
    }

    public static FlowWithSource of(Flow flow) {
        return FlowWithSource.of(flow, "");
    }
}
