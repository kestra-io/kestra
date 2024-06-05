package io.kestra.core.services;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.condition.ExecutionFlowCondition;
import io.kestra.plugin.core.condition.ExecutionNamespaceCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@MicronautTest
class ConditionServiceTest {
    @Inject
    ConditionService conditionService;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        RunContext runContext = runContextFactory.of(flow, execution);
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, execution);

        List<Condition> conditions = Arrays.asList(
            ExecutionFlowCondition.builder()
                .namespace(flow.getNamespace())
                .flowId(flow.getId())
                .build(),
            ExecutionNamespaceCondition.builder()
                .namespace(flow.getNamespace())
                .build()
        );


        boolean valid = conditionService.valid(flow, conditions, conditionContext);

        assertThat(valid, is(true));
    }

    @Test
    void exception() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Schedule schedule = Schedule.builder().id("unit").type(Schedule.class.getName()).cron("0 0 1 * *").build();

        RunContext runContext = runContextFactory.of(flow, schedule);
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);

        List<Condition> conditions = Collections.singletonList(
            ExecutionFlowCondition.builder()
                .namespace(flow.getNamespace())
                .flowId(flow.getId())
                .build()
        );

        conditionService.valid(flow, conditions, conditionContext);

        LogEntry matchingLog = TestsUtils.awaitLog(logs, logEntry -> logEntry.getNamespace().equals("io.kestra.core.services.conditionservicetest") && logEntry.getFlowId().equals("exception"));
        receive.blockLast();
        assertThat(matchingLog, notNullValue());
    }
}
