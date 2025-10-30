package io.kestra.core.repositories;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;

import java.util.Collections;

class ExecutionFixture {
    public static Execution EXECUTION_1(String tenant) {
        return Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .tenantId(tenant)
            .flowId("full")
            .flowRevision(1)
            .state(new State())
            .inputs(ImmutableMap.of("test", "value"))
            .taskRunList(Collections.singletonList(
                TaskRun.builder()
                    .id(IdUtils.create())
                    .namespace("io.kestra.unittest")
                    .flowId("full")
                    .state(new State())
                    .attempts(Collections.singletonList(
                        TaskRunAttempt.builder()
                            .build()
                    ))
                    .outputs(Variables.inMemory(ImmutableMap.of(
                        "out", "value"
                    )))
                    .build()
            ))
            .build();
    }

    public static Execution EXECUTION_2(String tenant) {
        return Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .tenantId(tenant)
            .flowId("full")
            .flowRevision(1)
            .state(new State())
            .inputs(ImmutableMap.of("test", 1))
            .taskRunList(Collections.singletonList(
                TaskRun.builder()
                    .id(IdUtils.create())
                    .namespace("io.kestra.unittest")
                    .flowId("full")
                    .state(new State())
                    .attempts(Collections.singletonList(
                        TaskRunAttempt.builder()
                            .build()
                    ))
                    .outputs(Variables.inMemory(ImmutableMap.of(
                        "out", 1
                    )))
                    .build()
            ))
            .build();
    }

    public static Execution EXECUTION_TEST(String tenant) {
        return Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .tenantId(tenant)
            .flowId("full")
            .flowRevision(1)
            .state(new State())
            .inputs(ImmutableMap.of("test", 1))
            .kind(ExecutionKind.TEST)
            .taskRunList(Collections.singletonList(
                TaskRun.builder()
                    .id(IdUtils.create())
                    .namespace("io.kestra.unittest")
                    .flowId("full")
                    .state(new State())
                    .attempts(Collections.singletonList(
                        TaskRunAttempt.builder()
                            .build()
                    ))
                    .outputs(Variables.inMemory(ImmutableMap.of(
                        "out", 1
                    )))
                    .build()
            ))
            .build();
    }
}