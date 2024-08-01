package io.kestra.core.services;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.condition.ExpressionCondition;
import io.kestra.plugin.core.trigger.Schedule;
import jakarta.inject.Inject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

@KestraTest
class PluginDefaultServiceTest {
    private static final Map<String, Object> TEST_FLOW_AS_MAP = Map.of(
        "id", "test",
        "namespace", "type",
        "tasks", List.of(
            Map.of("id", "my-task", "type", "io.kestra.test")
        )
    );

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Test
    void shouldInjectGivenDefaultsIncludingType() {
        // Given
        Map<String, List<PluginDefault>> defaults = Map.of(
            "io.kestra.test",
            List.of(new PluginDefault("io.kestra.test", false, Map.of("taskRunner", Map.of("type", "io.kestra.test"))))
        );

        // When
        Object result = pluginDefaultService.recursiveDefaults(TEST_FLOW_AS_MAP, defaults);

        // Then
        Assertions.assertEquals(Map.of(
            "id", "test",
            "namespace", "type",
            "tasks", List.of(
                Map.of(
                    "id", "my-task",
                    "type", "io.kestra.test",
                    "taskRunner", Map.of("type", "io.kestra.test")
                )
            )
        ), result);
    }

    @Test
    void shouldInjectGivenSimpleDefaults() {
        // Given
        Map<String, List<PluginDefault>> defaults = Map.of(
            "io.kestra.test",
            List.of(new PluginDefault("io.kestra.test", false, Map.of("default-key", "default-value")))
        );

        // When
        Object result = pluginDefaultService.recursiveDefaults(TEST_FLOW_AS_MAP, defaults);

        // Then
        Assertions.assertEquals(Map.of(
            "id", "test",
            "namespace", "type",
            "tasks", List.of(
                Map.of(
                    "id", "my-task",
                    "type", "io.kestra.test",
                    "default-key", "default-value"
                )
            )
        ), result);
    }

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
                    .conditions(List.of(ExpressionCondition.builder()
                        .type(ExpressionCondition.class.getName())
                        .build())
                    )
                    .build()
            ))
            .tasks(Collections.singletonList(task))
            .pluginDefaults(List.of(
                new PluginDefault(DefaultTester.class.getName(), false, ImmutableMap.of(
                    "value", 1,
                    "set", 123,
                    "arrays", Collections.singletonList(1)
                )),
                new PluginDefault(DefaultTriggerTester.class.getName(), false, ImmutableMap.of(
                    "set", 123
                )),
                new PluginDefault(ExpressionCondition.class.getName(), false, ImmutableMap.of(
                    "expression", "{{ test }}"
                ))
            ))
            .build();

        Flow injected = pluginDefaultService.injectDefaults(flow);

        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(1));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(666));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getDoubleValue(), is(19D));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getArrays().size(), is(2));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getArrays(), containsInAnyOrder(1, 2));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getProperty().getHere(), is("me"));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getProperty().getLists().size(), is(1));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getProperty().getLists().getFirst().getVal().size(), is(1));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getProperty().getLists().getFirst().getVal().get("key"), is("test"));
        assertThat(((DefaultTriggerTester) injected.getTriggers().getFirst()).getSet(), is(123));
        assertThat(((ExpressionCondition) injected.getTriggers().getFirst().getConditions().getFirst()).getExpression(), is("{{ test }}"));
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
            .pluginDefaults(List.of(
                new PluginDefault(DefaultTester.class.getName(), true, ImmutableMap.of(
                    "set", 123
                )),
                new PluginDefault(DefaultTester.class.getName(), true, ImmutableMap.of(
                    "set", 789
                )),
                new PluginDefault(DefaultTester.class.getName(), false, ImmutableMap.of(
                    "value", 1,
                    "set", 456,
                    "arrays", Collections.singletonList(1)
                ))
            ))
            .build();

        Flow injected = pluginDefaultService.injectDefaults(flow);

        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(123));
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
                    .conditions(List.of(ExpressionCondition.builder()
                        .type(ExpressionCondition.class.getName())
                        .build())
                    )
                    .build()
            ))
            .tasks(Collections.singletonList(task))
            .pluginDefaults(List.of(
                new PluginDefault(DefaultTester.class.getName(), false, ImmutableMap.of(
                    "set", 789
                )),
                new PluginDefault("io.kestra.core.services.", false, ImmutableMap.of(
                    "value", 2,
                    "set", 456,
                    "arrays", Collections.singletonList(1)
                )),
                new PluginDefault("io.kestra.core.services2.", false, ImmutableMap.of(
                    "value", 3
                ))
            ))
            .build();

        Flow injected = pluginDefaultService.injectDefaults(flow);

        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(666));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(2));
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
            .pluginDefaults(List.of(
                new PluginDefault("io.kestra.core.services.DefaultTesterAlias", false, ImmutableMap.of(
                    "value", 1
                ))
            ))
            .build();

        Flow injected = pluginDefaultService.injectDefaults(flow);

        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(1));
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