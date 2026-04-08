package io.kestra.core.services;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ConditionServiceTest {
    @Inject
    private ConditionService conditionService;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private DispatchQueueInterface<LogEntry> logQueue;

    @Test
    void valid() throws InternalException {
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

        boolean valid = conditionService.areValid(conditions, conditionContext);

        assertThat(valid).isTrue();
    }

    @Test
    void exception() {
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

        var exception = assertThrows(IllegalConditionEvaluation.class, () -> conditionService.areValid(conditions, conditionContext));
        assertThat(exception.getMessage()).isEqualTo("Invalid condition with null execution");
    }

    @Test
    void areValidEmptyListReturnsTrue() throws InternalException {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        RunContext runContext = runContextFactory.of(flow, execution);
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, execution);

        // When
        boolean valid = conditionService.areValid(Collections.emptyList(), conditionContext);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void conditionContextBuildsCorrectContext() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        RunContext runContext = runContextFactory.of(flow, execution);

        // When
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, execution);

        // Then
        assertThat(conditionContext.getFlow()).isEqualTo(flow);
        assertThat(conditionContext.getExecution()).isEqualTo(execution);
        assertThat(conditionContext.getRunContext()).isEqualTo(runContext);
        assertThat(conditionContext.getMultipleConditionStorage()).isNull();
    }

    @Test
    void conditionContextWithMultipleConditionStorageParam() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        RunContext runContext = runContextFactory.of(flow, execution);
        MultipleConditionStateStore stateStore = Mockito.mock(MultipleConditionStateStore.class);

        // When
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, execution, stateStore);

        // Then
        assertThat(conditionContext.getFlow()).isEqualTo(flow);
        assertThat(conditionContext.getExecution()).isEqualTo(execution);
        assertThat(conditionContext.getRunContext()).isEqualTo(runContext);
        assertThat(conditionContext.getMultipleConditionStorage()).isSameAs(stateStore);
    }

    // --- isValid(Condition, FlowInterface, Execution) ---

    @Test
    void isValidSingleConditionTrue() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        Condition condition = ExecutionFlow.builder()
            .namespace(Property.ofValue(flow.getNamespace()))
            .flowId(Property.ofValue(flow.getId()))
            .build();

        // When
        boolean valid = conditionService.isValid(condition, flow, execution);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void isValidSingleConditionFalse() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        Condition condition = ExecutionFlow.builder()
            .namespace(Property.ofValue("other.namespace"))
            .flowId(Property.ofValue(flow.getId()))
            .build();

        // When
        boolean valid = conditionService.isValid(condition, flow, execution);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void isValidSingleConditionExceptionReturnsFalseAndLogs() {
        // Given
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        Condition throwingCondition = new Condition() {
            @Override
            public boolean test(ConditionContext conditionContext) throws InternalException {
                throw new InternalException("simulated condition failure");
            }
        };

        // When
        boolean valid = conditionService.isValid(throwingCondition, flow, execution);

        // Then
        assertThat(valid).isFalse();
        List<LogEntry> matchingLogs = TestsUtils.awaitLogs(
            logs,
            log -> log.getLevel() == Level.WARN && log.getMessage().contains("Evaluate Condition Failed"),
            1
        );
        assertThat(matchingLogs).hasSize(1);
    }

    @Test
    void isValidTriggerNoConditionsReturnsTrue() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        Schedule trigger = Schedule.builder()
            .id("unit")
            .type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .build();

        // When
        boolean valid = conditionService.isValid(trigger, flow, execution, null);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void isValidTriggerWhenFalseReturnsFalse() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        Schedule trigger = Schedule.builder()
            .id("unit")
            .type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .when("false")
            .build();

        // When
        boolean valid = conditionService.isValid(trigger, flow, execution, null);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void isValidTriggerWhenInvalidExpressionReturnsFalseAndLogs() {
        // Given
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        // Malformed Pebble expression causes IllegalVariableEvaluationException during render
        Schedule trigger = Schedule.builder()
            .id("unit")
            .type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .when("{{ invalid-pebble-expression() }}")
            .build();

        // When
        boolean valid = conditionService.isValid(trigger, flow, execution, (MultipleConditionStateStore) null);

        // Then
        assertThat(valid).isFalse();
        List<LogEntry> matchingLogs = TestsUtils.awaitLogs(
            logs,
            log -> log.getLevel() == Level.WARN && log.getMessage().contains("Evaluate Condition Failed"),
            1
        );
        assertThat(matchingLogs).hasSize(1);
    }

    @Test
    void isValidTriggerValidConditionsReturnsTrue() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        Schedule trigger = Schedule.builder()
            .id("unit")
            .type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .conditions(List.of(
                ExecutionFlow.builder()
                    .namespace(Property.ofValue(flow.getNamespace()))
                    .flowId(Property.ofValue(flow.getId()))
                    .build()
            ))
            .build();

        // When
        boolean valid = conditionService.isValid(trigger, flow, execution, null);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void isValidTriggerConditionExceptionReturnsFalseAndLogs() {
        // Given
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        Condition throwingCondition = new Condition() {
            @Override
            public boolean test(ConditionContext conditionContext) throws InternalException {
                throw new InternalException("simulated condition failure");
            }
        };
        Schedule trigger = Schedule.builder()
            .id("unit")
            .type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .conditions(List.of(throwingCondition))
            .build();

        // When
        boolean valid = conditionService.isValid(trigger, flow, execution, null);

        // Then
        assertThat(valid).isFalse();
        List<LogEntry> matchingLogs = TestsUtils.awaitLogs(
            logs,
            log -> log.getLevel() == Level.WARN && log.getMessage().contains("Evaluate Condition Failed"),
            1
        );
        assertThat(matchingLogs).hasSize(1);
    }

    @Test
    void isValidTriggerWithRunContextWhenFalseReturnsFalse() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        RunContext runContext = runContextFactory.of(flow, execution);
        Schedule trigger = Schedule.builder()
            .id("unit")
            .type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .when("false")
            .build();

        // When
        boolean valid = conditionService.isValid(trigger, flow, execution, runContext, null);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void isValidTriggerWithRunContextValidConditionsReturnsTrue() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        RunContext runContext = runContextFactory.of(flow, execution);
        Schedule trigger = Schedule.builder()
            .id("unit")
            .type(Schedule.class.getName())
            .cron("0 0 1 * *")
            .conditions(List.of(
                ExecutionFlow.builder()
                    .namespace(Property.ofValue(flow.getNamespace()))
                    .flowId(Property.ofValue(flow.getId()))
                    .build()
            ))
            .build();

        // When
        boolean valid = conditionService.isValid(trigger, flow, execution, runContext, null);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void isValidMultipleConditionNullReturnsTrue() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        // When
        boolean valid = conditionService.isValid((MultipleCondition) null, flow, execution, null);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void isValidMultipleConditionNullConditionsMapReturnsTrue() {
        // Given
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        MultipleCondition multipleCondition = new MultipleCondition() {
            @Override
            public String getId() { return "test"; }

            @Override
            public TimeWindow getTimeWindow() { return null; }

            @Override
            public Boolean getResetOnSuccess() { return null; }

            @Override
            public Map<String, Condition> getConditions() { return null; }

            @Override
            public Logger logger() { return LoggerFactory.getLogger(ConditionServiceTest.class); }
        };

        // When
        boolean valid = conditionService.isValid(multipleCondition, flow, execution, null);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void isValidMultipleConditionExceptionReturnsFalseAndLogs() {
        // Given
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        MultipleCondition throwingCondition = new MultipleCondition() {
            @Override
            public String getId() { return "test"; }

            @Override
            public TimeWindow getTimeWindow() { return null; }

            @Override
            public Boolean getResetOnSuccess() { return null; }

            @Override
            public Map<String, Condition> getConditions() {
                return Map.of(
                    "condition_1",
                    ExecutionFlow.builder()
                        .namespace(Property.ofValue(flow.getNamespace()))
                        .flowId(Property.ofValue(flow.getId()))
                        .build()
                );
            }

            @Override
            public Logger logger() { return LoggerFactory.getLogger(ConditionServiceTest.class); }

            @Override
            public boolean test(ConditionContext conditionContext) throws InternalException {
                throw new InternalException("simulated evaluation failure");
            }
        };

        // When
        boolean valid = conditionService.isValid(throwingCondition, flow, execution, null);

        // Then
        assertThat(valid).isFalse();
        List<LogEntry> matchingLogs = TestsUtils.awaitLogs(
            logs,
            log -> log.getLevel() == Level.WARN && log.getMessage().contains("Evaluate Condition Failed"),
            1
        );
        assertThat(matchingLogs).hasSize(1);
    }
}
