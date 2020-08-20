package org.kestra.repository.elasticsearch;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.executions.TaskRunAttempt;
import org.kestra.core.models.executions.metrics.Counter;
import org.kestra.core.models.executions.metrics.Timer;
import org.kestra.core.models.flows.State;
import org.slf4j.event.Level;

import java.time.Duration;
import java.util.Collections;

class ExecutionFixture {
    public static final Execution EXECUTION_1 = Execution.builder()
        .id(FriendlyId.createFriendlyId())
        .namespace("org.kestra.unittest")
        .flowId("full")
        .flowRevision(1)
        .state(new State())
        .inputs(ImmutableMap.of("test", "value"))
        .taskRunList(Collections.singletonList(
            TaskRun.builder()
                .id(FriendlyId.createFriendlyId())
                .namespace("org.kestra.unittest")
                .flowId("full")
                .state(new State())
                .attempts(Collections.singletonList(
                    TaskRunAttempt.builder()
                        .metrics(Collections.singletonList(Counter.of("counter", 1)))
                        .build()
                ))
                .outputs(ImmutableMap.of(
                    "out", "value"
                ))
                .build()
        ))
        .build();

    public static final Execution EXECUTION_2 = Execution.builder()
        .id(FriendlyId.createFriendlyId())
        .namespace("org.kestra.unittest")
        .flowId("full")
        .flowRevision(1)
        .state(new State())
        .inputs(ImmutableMap.of("test", 1))
        .taskRunList(Collections.singletonList(
            TaskRun.builder()
                .id(FriendlyId.createFriendlyId())
                .namespace("org.kestra.unittest")
                .flowId("full")
                .state(new State())
                .attempts(Collections.singletonList(
                    TaskRunAttempt.builder()
                        .metrics(Collections.singletonList(Timer.of("test", Duration.ofMillis(150))))
                        .build()
                ))
                .outputs(ImmutableMap.of(
                    "out", 1
                ))
                .build()
        ))
        .build();
}
