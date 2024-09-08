package io.kestra.core.repositories;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;

import java.time.Duration;
import java.util.Collections;

class ExecutionFixture {
    public static final Execution EXECUTION_1 = Execution.builder()
        .id(IdUtils.create())
        .namespace("io.kestra.unittest")
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
                .outputs(Variables.of(ImmutableMap.of(
                    "out", "value"
                )))
                .build()
        ))
        .build();

    public static final Execution EXECUTION_2 = Execution.builder()
        .id(IdUtils.create())
        .namespace("io.kestra.unittest")
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
                .outputs(Variables.of(ImmutableMap.of(
                    "out", 1
                )))
                .build()
        ))
        .build();
}
