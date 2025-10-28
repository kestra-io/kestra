package io.kestra.core.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.condition.Expression;
import io.kestra.plugin.core.log.Log;
import io.kestra.plugin.core.trigger.Schedule;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Duration;
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
    public static final String TEST_LOG_FLOW_SOURCE = """
            id: test
            namespace: io.kestra.unittest
            tasks:
             - id: log
               type: io.kestra.plugin.core.log.Log
        """;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Test
    void shouldInjectGivenFlowWithNullSource() throws FlowProcessingException {
        // Given
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowInterface flow = GenericFlow.fromYaml(tenant, TEST_LOG_FLOW_SOURCE);

        // When
        FlowWithSource result = pluginDefaultService.injectAllDefaults(flow, true);

        // Then
        Log task = (Log) result.getTasks().getFirst();
        assertThat(task.getMessage(), is("This is a default message"));
    }

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
    public void injectFlowAndGlobals() throws FlowProcessingException, JsonProcessingException {
        String source = String.format("""
            id: default-test
            namespace: io.kestra.tests

            triggers:
            - id: trigger
              type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTriggerTester
              conditions:
              - type: io.kestra.plugin.core.condition.ExpressionCondition

            tasks:
            - id: test
              type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
              set: 666

            pluginDefaults:
            - type: "%s"
              forced: false
              values:
                set: 123
                value: 1
                arrays: [1]
            - type: "%s"
              forced: false
              values:
                set: 123
            - type: "%s"
              forced: false
              values:
                expression: "{{ test }}"
            """,
            DefaultTester.class.getName(),
            DefaultTriggerTester.class.getName(),
            Expression.class.getName()
        );
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

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
        assertThat(((Expression) injected.getTriggers().getFirst().getConditions().getFirst()).getExpression().toString(), is("{{ test }}"));
    }

    @Test
    public void shouldInjectForcedDefaultsGivenForcedTrue() throws FlowProcessingException {
        // Given
        String source = """
            id: default-test
            namespace: io.kestra.tests

            tasks:
            - id: test
              type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
              set: 1

            pluginDefaults:
            - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
              forced: true
              values:
                set: 2
            - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
              forced: true
              values:
                set: 3
            - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
              forced: false
              values:
                set: 4
                value: 1
                arrays: [1]
        """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(2));
    }

    @Test
    public void shouldInjectDefaultGivenPrefixType() throws FlowProcessingException {
        // Given
        String source = """
            id: default-test
            namespace: io.kestra.tests

            triggers:
            - id: trigger
              type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTriggerTester
              conditions:
              - type: io.kestra.plugin.core.condition.ExpressionCondition

            tasks:
            - id: test
              type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
              set: 666

            pluginDefaults:
            - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
              values:
                set: 789
            - type: io.kestra.core.services.
              values:
                set: 456
                value: 2
            - type: io.kestra.core.services2.
              values:
                value: 3
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(666));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(2));
    }

    @Test
    void shouldInjectFlowDefaultsGivenAlias() throws FlowProcessingException {
        // Given
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        GenericFlow flow = GenericFlow.fromYaml(tenant, """
              id: default-test
              namespace: io.kestra.tests

              tasks:
              - id: test
                type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                set: 666

              pluginDefaults:
                 - type: io.kestra.core.services.DefaultTesterAlias
                   values:
                     value: 1
            """
        );
        // When
        FlowWithSource injected = pluginDefaultService.injectAllDefaults(flow, true);

        // Then
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(1));
    }

    @Test
    void shouldInjectFlowDefaultsGivenType() throws FlowProcessingException {
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        GenericFlow flow = GenericFlow.fromYaml(tenant, """
                  id: default-test
                  namespace: io.kestra.tests

                  tasks:
                  - id: test
                    type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                    set: 666

                  pluginDefaults:
                     - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                       values:
                         defaultValue: overridden
            """
        );

        FlowWithSource injected = pluginDefaultService.injectAllDefaults(flow, true);
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getDefaultValue(), is("overridden"));
    }

    @Test
    public void shouldNotInjectDefaultsGivenExistingTaskValue() throws FlowProcessingException {
        // Given
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        GenericFlow flow = GenericFlow.fromYaml(tenant, """
            id: default-test
            namespace: io.kestra.tests

            tasks:
            - id: test
              type: io.kestra.plugin.core.log.Log
              message: testing
              level: INFO

            pluginDefaults:
             - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
               values:
                 defaultValue: WARN
          """
        );

        // When
        FlowWithSource injected = pluginDefaultService.injectAllDefaults(flow, true);

        // Then
        assertThat(((Log) injected.getTasks().getFirst()).getLevel().toString(), is(Level.INFO.name()));
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

        @Builder.Default
        private String defaultValue = "default";

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

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin(aliases = "io.kestra.core.services.DefaultPrecedenceTesterAlias")
    public static class DefaultPrecedenceTester extends Task implements RunnableTask<VoidOutput> {
        private String propFoo;

        private String propBar;

        private String propBaz;

        @Override
        public VoidOutput run(RunContext runContext) throws Exception {
            return null;
        }
    }
}