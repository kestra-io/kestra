package io.kestra.executor.handler;

import java.util.List;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

final class Fixtures {
    private Fixtures() {
        // utility class pattern
    }

    static Flow flow() {
        return Flow.builder()
            .tenantId("tenant")
            .namespace("namespace")
            .id(IdUtils.create())
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("Hello World").build()))
            .build();
    }
}
