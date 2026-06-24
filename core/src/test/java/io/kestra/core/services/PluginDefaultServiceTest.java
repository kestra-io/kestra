package io.kestra.core.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
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
        Assertions.assertEquals(
            Map.of(
                "id", "test",
                "namespace", "type",
                "tasks", List.of(
                    Map.of(
                        "id", "my-task",
                        "type", "io.kestra.test",
                        "taskRunner", Map.of("type", "io.kestra.test")
                    )
                )
            ), result
        );
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
        Assertions.assertEquals(
            Map.of(
                "id", "test",
                "namespace", "type",
                "tasks", List.of(
                    Map.of(
                        "id", "my-task",
                        "type", "io.kestra.test",
                        "default-key", "default-value"
                    )
                )
            ), result
        );
    }

    @Test
    public void injectFlowAndGlobals() throws FlowProcessingException, JsonProcessingException {
        String source = String.format(
            """
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

        // Then — forced defaults follow admin-first precedence (kestra-ee#8262): they stamp over the
        // accumulated result, so the LAST matching forced default wins. With two same-level forced
        // defaults the later-declared one (set: 3) wins, not the earlier one.
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(3));
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
        GenericFlow flow = GenericFlow.fromYaml(
            tenant, """
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
        GenericFlow flow = GenericFlow.fromYaml(
            tenant, """
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
    void shouldInjectFlowDefaultsGivenDeprecatedTaskDefaults() throws FlowProcessingException {
        // Given
        String source = """
                id: default-test
                namespace: io.kestra.tests

                tasks:
                - id: test
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  set: 666

                taskDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  forced: false
                  values:
                    set: 123
                    value: 1
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then - taskDefaults should behave identically to pluginDefaults
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(1));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(666)); // task value takes precedence over non-forced default
    }

    @Test
    void shouldInjectForcedDefaultsGivenDeprecatedTaskDefaults() throws FlowProcessingException {
        // Given - forced defaults declared via the deprecated 'taskDefaults' key
        String source = """
                id: default-test
                namespace: io.kestra.tests

                tasks:
                - id: test
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  set: 666

                taskDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  forced: true
                  values:
                    set: 123
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then - forced default overrides the task-level value
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(123));
    }

    @Test
    void shouldPreferPluginDefaultsOverTaskDefaults() throws FlowProcessingException {
        // Given - flow has both pluginDefaults and taskDefaults; pluginDefaults should win
        String source = """
                id: default-test
                namespace: io.kestra.tests

                tasks:
                - id: test
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  set: 666

                pluginDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  values:
                    value: 42

                taskDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  values:
                    value: 99
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then - pluginDefaults takes precedence
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(42));
    }

    @Test
    public void shouldNotInjectDefaultsGivenExistingTaskValue() throws FlowProcessingException {
        // Given
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        GenericFlow flow = GenericFlow.fromYaml(
            tenant, """
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

    @Test
    void shouldApplyRefDefaultToNestedTaskRunner() {
        // a nested taskRunner carrying its own pluginDefaultsRef receives the referenced plugin-defaults
        Map<String, Object> flow = Map.of(
            "id", "test",
            "namespace", "type",
            "tasks", List.of(
                Map.of(
                    "id", "my-task",
                    "type", "io.kestra.test",
                    "taskRunner", Map.of("type", "io.kestra.runner", "pluginDefaultsRef", "runner-cfg")
                )
            )
        );
        Map<String, PluginDefaultService.RefMatch> refDefaults = Map.of(
            "runner-cfg", new PluginDefaultService.RefMatch(
                new PluginDefault("io.kestra.runner", false, "runner-cfg", Map.of("cpus", 4)),
                java.util.Set.of("io.kestra.runner")
            )
        );

        // When — apply then consume the marker (the two passes the injection pipeline runs)
        Object applied = pluginDefaultService.recursiveRefDefaults(flow, refDefaults, java.util.Set.of());
        Object result = pluginDefaultService.stripAppliedRefMarkers(applied, refDefaults);

        // Then — type matches (io.kestra.runner), values applied, marker consumed
        Assertions.assertEquals(
            Map.of(
                "id", "test",
                "namespace", "type",
                "tasks", List.of(
                    Map.of(
                        "id", "my-task",
                        "type", "io.kestra.test",
                        "taskRunner", Map.of("type", "io.kestra.runner", "cpus", 4)
                    )
                )
            ), result
        );
    }

    @Test
    void shouldNotApplyRefDefaultOnTypeMismatch() {
        // the named plugin-defaults is declared for a different type than the referencing plugin
        Map<String, Object> flow = Map.of(
            "id", "test",
            "namespace", "type",
            "tasks", List.of(
                Map.of("id", "my-task", "type", "io.kestra.other", "pluginDefaultsRef", "runner-cfg")
            )
        );
        Map<String, PluginDefaultService.RefMatch> refDefaults = Map.of(
            "runner-cfg", new PluginDefaultService.RefMatch(
                new PluginDefault("io.kestra.runner", false, "runner-cfg", Map.of("cpus", 4)),
                java.util.Set.of("io.kestra.runner")
            )
        );

        // When
        Object applied = pluginDefaultService.recursiveRefDefaults(flow, refDefaults, java.util.Set.of());
        Object result = pluginDefaultService.stripAppliedRefMarkers(applied, refDefaults);

        // Then — not applied (no 'cpus'), marker kept so it surfaces as unresolved/inapplicable
        Assertions.assertEquals(
            Map.of(
                "id", "test",
                "namespace", "type",
                "tasks", List.of(
                    Map.of("id", "my-task", "type", "io.kestra.other", "pluginDefaultsRef", "runner-cfg")
                )
            ), result
        );
    }

    @Test
    void shouldApplyRefDefaultDeclaredWithAliasType() throws FlowProcessingException {
        // the named default is declared with a plugin alias; addAliases canonicalizes it so it still matches
        // a task referencing it by the canonical type
        String source = """
                id: ref-alias-test
                namespace: io.kestra.tests

                tasks:
                - id: referencing
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  pluginDefaultsRef: cfg

                pluginDefaults:
                - type: io.kestra.core.services.DefaultTesterAlias
                  ref: cfg
                  values:
                    value: 7
            """;

        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // applied despite the alias/canonical type mismatch, and the marker consumed
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(7));
        assertThat(injected.getTasks().getFirst().getPluginDefaultsRef(), is((String) null));
    }

    @Test
    void shouldApplyRefDefaultOnlyToReferencingPlugin() throws FlowProcessingException {
        // Given — same-type tasks; only one opts into the named default
        String source = """
                id: ref-test
                namespace: io.kestra.tests

                tasks:
                - id: referencing
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  pluginDefaultsRef: cfg
                - id: plain
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester

                pluginDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  ref: cfg
                  values:
                    value: 7
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(7));
        assertThat(((DefaultTester) injected.getTasks().get(1)).getValue(), is((Integer) null));
    }

    @Test
    void shouldSuppressTypeMatchedDefaultWhenRefIsSet() throws FlowProcessingException {
        // Given — a type-matched default AND a ref default; the referencing task must get only the ref default
        String source = """
                id: ref-suppress-test
                namespace: io.kestra.tests

                tasks:
                - id: referencing
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  pluginDefaultsRef: cfg

                pluginDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  values:
                    value: 1
                    defaultValue: typed
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  ref: cfg
                  values:
                    value: 7
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then — ref default applied, type-matched default ('defaultValue') suppressed
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is(7));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getDefaultValue(), is("default"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldEnforceForcedTypeDefaultOnRefPluginAndWarn() {
        // capture WARN from PluginDefaultService logger
        Logger serviceLogger = (Logger) LoggerFactory.getLogger(PluginDefaultService.class);
        List<ILoggingEvent> capturedLogs = new ArrayList<>();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                capturedLogs.add(event);
            }
        };
        appender.setContext(serviceLogger.getLoggerContext());
        appender.start();
        serviceLogger.addAppender(appender);

        try {
            Map<String, Object> flow = Map.of(
                "id", "test",
                "namespace", "type",
                "tasks", List.of(
                    Map.of("id", "my-task", "type", "io.kestra.test", "pluginDefaultsRef", "cfg")
                )
            );
            Map<String, List<PluginDefault>> forced = Map.of(
                "io.kestra.test",
                List.of(new PluginDefault("io.kestra.test", true, Map.of("forced-key", "enforced")))
            );

            // When — forced pass over a plugin that opted into a named default
            Object result = pluginDefaultService.recursiveDefaults(flow, forced, true);

            // Then — forced default is enforced despite pluginDefaultsRef, and a WARN is logged
            Map<String, Object> task = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) result).get("tasks")).getFirst();
            assertThat(task.get("forced-key"), is("enforced"));
            assertThat(
                capturedLogs.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .anyMatch(e -> e.getFormattedMessage().contains("pluginDefaultsRef")),
                is(true)
            );
        } finally {
            serviceLogger.detachAppender(appender);
        }
    }

    @Test
    void shouldYieldToTaskValueForNonForcedRef() throws FlowProcessingException {
        // Given — non-forced ref default, task sets the property explicitly
        String source = """
                id: ref-nonforced-test
                namespace: io.kestra.tests

                tasks:
                - id: referencing
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  pluginDefaultsRef: cfg
                  set: 1

                pluginDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  ref: cfg
                  values:
                    set: 99
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then — explicit task value wins
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(1));
    }

    @Test
    void shouldLetFlowForcedRefOverrideTaskValue() throws FlowProcessingException {
        // Given — 'forced: true' on a flow-level ref default IS honored on this release line (1.3 lets flows
        // enforce, unlike develop where flow-level 'forced' was removed): the forced ref overrides the task value.
        // (A namespace- or global-forced ref of the same name would still win via admin-first precedence.)
        String source = """
                id: ref-forced-test
                namespace: io.kestra.tests

                tasks:
                - id: referencing
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  pluginDefaultsRef: cfg
                  set: 1

                pluginDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  ref: cfg
                  forced: true
                  values:
                    set: 99
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then — flow-level 'forced' is honored, so the ref value overrides the explicit task value
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(99));
    }

    @Test
    void shouldIgnoreUnknownRef() throws FlowProcessingException {
        // Given — task references a ref that does not exist
        String source = """
                id: ref-unknown-test
                namespace: io.kestra.tests

                tasks:
                - id: referencing
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  pluginDefaultsRef: nope
                  set: 5

                pluginDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  ref: cfg
                  values:
                    value: 7
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then — task unchanged; the 'cfg' default is not applied by type either
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(5));
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getValue(), is((Integer) null));
        assertThat(injected.getTasks().getFirst().getPluginDefaultsRef(), is("nope"));
    }

    @Test
    void shouldStrictParseRefFields() throws FlowProcessingException {
        // Given — strict parsing must accept the new fields: pluginDefaultsRef on the task, ref + forced on the default
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        GenericFlow flow = GenericFlow.fromYaml(
            tenant, """
                  id: ref-strict-test
                  namespace: io.kestra.tests

                  tasks:
                  - id: referencing
                    type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                    pluginDefaultsRef: cfg
                    set: 1

                  pluginDefaults:
                  - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                    ref: cfg
                    forced: true
                    values:
                      set: 99
                """
        );

        // When — strictParsing = true
        FlowWithSource injected = pluginDefaultService.injectAllDefaults(flow, true);

        // Then — ref resolved; 'forced' is honored at flow level on this release line, so the ref value wins,
        // and the marker is consumed (stripped) once the default is applied
        assertThat(((DefaultTester) injected.getTasks().getFirst()).getSet(), is(99));
        assertThat(injected.getTasks().getFirst().getPluginDefaultsRef(), is((String) null));
    }

    @Test
    void shouldReportUnresolvedRefForValidation() throws FlowProcessingException {
        // Given — one resolvable ref and one unknown ref
        String source = """
                id: ref-validate-test
                namespace: io.kestra.tests

                tasks:
                - id: ok
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  pluginDefaultsRef: known
                - id: broken
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  pluginDefaultsRef: missing

                pluginDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester
                  ref: known
                  values:
                    value: 1
            """;

        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // When / Then — only the unknown ref survives and is reported
        assertThat(pluginDefaultService.unresolvedPluginDefaultsRefs(injected), is(java.util.Set.of("missing")));
    }

    @Test
    void shouldApplyRefDefaultToTrigger() throws FlowProcessingException {
        // Given — a trigger opts into a named default
        String source = """
                id: ref-trigger-test
                namespace: io.kestra.tests

                triggers:
                - id: trigger
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTriggerTester
                  pluginDefaultsRef: cfg

                tasks:
                - id: test
                  type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTester

                pluginDefaults:
                - type: io.kestra.core.services.PluginDefaultServiceTest$DefaultTriggerTester
                  ref: cfg
                  values:
                    set: 42
            """;

        // When
        var tenant = TestsUtils.randomTenant(PluginDefaultServiceTest.class.getSimpleName());
        FlowWithSource injected = pluginDefaultService.parseFlowWithAllDefaults(tenant, source, false);

        // Then
        assertThat(((DefaultTriggerTester) injected.getTriggers().getFirst()).getSet(), is(42));
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