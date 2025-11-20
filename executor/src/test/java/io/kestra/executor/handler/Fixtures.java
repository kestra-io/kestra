package io.kestra.executor.handler;

import io.kestra.core.models.flows.Flow;
import io.kestra.plugin.core.log.Log;

import java.util.List;

final class Fixtures {
    private Fixtures() {
        // utility class pattern
    }

    static Flow flow() {
        return Flow.builder()
            .tenantId("tenant")
            .namespace("namespace")
            .id("flow")
            .revision(1)
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("Hello World").build()))
            .build();
    }
}
