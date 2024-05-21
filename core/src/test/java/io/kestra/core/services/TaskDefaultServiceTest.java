package io.kestra.core.services;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.plugin.core.condition.VariableCondition;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.TaskDefault;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.core.runners.RunContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

@MicronautTest
class TaskDefaultServiceTest {
    @Inject
    private TaskDefaultService taskDefaultService;

    @Test
    public void injectFlowAndGlobals() {
        DefaultTester task = DefaultTester.builder()
            .id("test")
            .type(DefaultTester.class.getName())
            .set(666)
            .build();

        Flow flow = Flow.builder()
            .triggers(List.of(
                DefaultTriggerTester.builder()
                    .id("trigger")
                    .type(DefaultTriggerTester.class.getName())
                    .conditions(List.of(VariableCondition.builder()
                        .type(VariableCondition.class.getName())
                        .build())
                    )
                    .build()
            ))
            .tasks(Collections.singletonList(task))
            .taskDefaults(List.of(
                new TaskDefault(DefaultTester.class.getName(), false, ImmutableMap.of(
                    "value", 1,
                    "set", 123,
                    "arrays", Collections.singletonList(1)
                )),
                new TaskDefault(DefaultTriggerTester.class.getName(), false, ImmutableMap.of(
                    "set", 123
                )),
                new TaskDefault(VariableCondition.class.getName(), false, ImmutableMap.of(
                    "expression", "{{ test }}"
                ))
            ))
            .build();

        Flow injected = taskDefaultService.injectDefaults(flow);

        assertThat(((DefaultTester) injected.getTasks().get(0)).getValue(), is(1));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getSet(), is(666));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getDoubleValue(), is(19D));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getArrays().size(), is(2));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getArrays(), containsInAnyOrder(1, 2));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getProperty().getHere(), is("me"));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getProperty().getLists().size(), is(1));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getProperty().getLists().get(0).getVal().size(), is(1));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getProperty().getLists().get(0).getVal().get("key"), is("test"));
        assertThat(((DefaultTriggerTester) injected.getTriggers().get(0)).getSet(), is(123));
        assertThat(((VariableCondition) injected.getTriggers().get(0).getConditions().get(0)).getExpression(), is("{{ test }}"));
    }

    @Test
    public void forced() {
        DefaultTester task = DefaultTester.builder()
            .id("test")
            .type(DefaultTester.class.getName())
            .set(666)
            .build();

        Flow flow = Flow.builder()
            .tasks(Collections.singletonList(task))
            .taskDefaults(List.of(
                new TaskDefault(DefaultTester.class.getName(), true, ImmutableMap.of(
                    "set", 123
                )),
                new TaskDefault(DefaultTester.class.getName(), true, ImmutableMap.of(
                    "set", 789
                )),
                new TaskDefault(DefaultTester.class.getName(), false, ImmutableMap.of(
                    "value", 1,
                    "set", 456,
                    "arrays", Collections.singletonList(1)
                ))
            ))
            .build();

        Flow injected = taskDefaultService.injectDefaults(flow);

        assertThat(((DefaultTester) injected.getTasks().get(0)).getSet(), is(123));
    }

    @Test
    public void prefix() {
        DefaultTester task = DefaultTester.builder()
            .id("test")
            .type(DefaultTester.class.getName())
            .set(666)
            .build();

        Flow flow = Flow.builder()
            .triggers(List.of(
                DefaultTriggerTester.builder()
                    .id("trigger")
                    .type(DefaultTriggerTester.class.getName())
                    .conditions(List.of(VariableCondition.builder()
                        .type(VariableCondition.class.getName())
                        .build())
                    )
                    .build()
            ))
            .tasks(Collections.singletonList(task))
            .taskDefaults(List.of(
                new TaskDefault(DefaultTester.class.getName(), false, ImmutableMap.of(
                    "set", 789
                )),
                new TaskDefault("io.kestra.core.services.", false, ImmutableMap.of(
                    "value", 2,
                    "set", 456,
                    "arrays", Collections.singletonList(1)
                )),
                new TaskDefault("io.kestra.core.services2.", false, ImmutableMap.of(
                    "value", 3
                ))
            ))
            .build();

        Flow injected = taskDefaultService.injectDefaults(flow);

        assertThat(((DefaultTester) injected.getTasks().get(0)).getSet(), is(666));
        assertThat(((DefaultTester) injected.getTasks().get(0)).getValue(), is(2));
    }

    @Test
    void alias() {
        DefaultTester task = DefaultTester.builder()
            .id("test")
            .type(DefaultTester.class.getName())
            .set(666)
            .build();

        Flow flow = Flow.builder()
            .tasks(Collections.singletonList(task))
            .taskDefaults(List.of(
                new TaskDefault("io.kestra.core.services.DefaultTesterAlias", false, ImmutableMap.of(
                    "value", 1
                ))
            ))
            .build();

        Flow injected = taskDefaultService.injectDefaults(flow);

        assertThat(((DefaultTester) injected.getTasks().get(0)).getValue(), is(1));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class DefaultTriggerTester extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Schedule.Output> {
        @Override
        public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
            return Optional.empty();
        }

        private Integer set;

        @Override
        public Duration getInterval() {
            return Duration.ofSeconds(1);
        }
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin(aliases = "io.kestra.core.services.DefaultTesterAlias")
    public static class DefaultTester extends Task implements RunnableTask<VoidOutput> {
        private Collections property;

        private Integer value;

        private Double doubleValue;

        private Integer set;

        private List<Integer> arrays;

        @Override
        public VoidOutput run(RunContext runContext) throws Exception {
            return null;
        }

        @NoArgsConstructor
        @Getter
        public static class Collections {
            private String here;
            private List<Lists> lists;

        }

        @NoArgsConstructor
        @Getter
        public static class Lists {
            private Map<String, String> val;
        }
    }
}