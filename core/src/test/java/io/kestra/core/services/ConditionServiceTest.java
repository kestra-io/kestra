package io.kestra.core.services;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.condition.ExecutionFlow;
import io.kestra.plugin.core.condition.ExecutionNamespace;
import io.kestra.plugin.core.trigger.Schedule;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ConditionServiceTest {
    @Inject
    ConditionService conditionService;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    private DispatchQueueInterface<LogEntry> logQueue;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        RunContext runContext = runContextFactory.of(flow, execution);
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, execution);

        List<Condition> conditions = Arrays.asList(
            ExecutionFlow.builder()
                .namespace(Property.ofValue(flow.getNamespace()))
                .flowId(Property.ofValue(flow.getId()))
                .build(),
            ExecutionNamespace.builder()
                .namespace(Property.ofValue(flow.getNamespace()))
                .build()
        );

        boolean valid = conditionService.valid(flow, conditions, conditionContext);

        assertThat(valid).isTrue();
    }

    @Test
    void exception() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Flow flow = TestsUtils.mockFlow();
        Schedule schedule = Schedule.builder().id("unit").type(Schedule.class.getName()).cron("0 0 1 * *").build();

        RunContext runContext = runContextFactory.of(flow, schedule);
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);

        List<Condition> conditions = Collections.singletonList(
            ExecutionFlow.builder()
                .namespace(Property.ofValue(flow.getNamespace()))
                .flowId(Property.ofValue(flow.getId()))
                .build()
        );

        conditionService.valid(flow, conditions, conditionContext);

        LogEntry matchingLog = TestsUtils
            .awaitLog(logs, logEntry -> logEntry.getNamespace().equals("io.kestra.core.services.conditionservicetest") && logEntry.getFlowId().equals("exception"));
        assertThat(matchingLog).isNotNull();
    }
}
