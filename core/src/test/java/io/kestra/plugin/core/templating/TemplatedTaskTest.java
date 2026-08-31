package io.kestra.plugin.core.templating;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.log.Log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class TemplatedTaskTest {

    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Test
    void templatedType() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("type", "io.kestra.plugin.core.debug.Return"));
        TemplatedTask templatedTask = TemplatedTask.builder()
            .id("template")
            .type(TemplatedTask.class.getName())
            .spec(Property.ofExpression("""
                type: {{ type }}
                format: It's alive!"""))
            .build();

        Output output = templatedTask.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output).isInstanceOf(Return.Output.class);
        assertThat(((Return.Output) output).getValue()).isEqualTo("It's alive!");
    }

    @Test
    void templatedFlowable() {
        RunContext runContext = runContextFactory.of();
        TemplatedTask templatedTask = TemplatedTask.builder()
            .id("template")
            .type(TemplatedTask.class.getName())
            .spec(Property.ofValue("""
                type: io.kestra.plugin.core.flow.Pause
                delay: PT10S"""))
            .build();

        var exception = assertThrows(IllegalArgumentException.class, () -> templatedTask.run(runContext));
        assertThat(exception.getMessage()).isEqualTo("The templated task must be a runnable task");
    }

    @Test
    void templatedTemplated() {
        RunContext runContext = runContextFactory.of();
        TemplatedTask templatedTask = TemplatedTask.builder()
            .id("template")
            .type(TemplatedTask.class.getName())
            .spec(Property.ofValue("""
                type: io.kestra.plugin.core.templating.TemplatedTask
                spec: whatever"""))
            .build();

        var exception = assertThrows(IllegalArgumentException.class, () -> templatedTask.run(runContext));
        assertThat(exception.getMessage()).isEqualTo("The templated task cannot be of type 'io.kestra.plugin.core.templating.TemplatedTask'");
    }

    @Test
    void shouldApplyDefaultWhenSpecOmitsPropertyGivenNonForcedPluginDefault() throws Exception {
        // Given
        TemplatedTask templatedTask = templatedTaskOf("""
            id: templated-defaults
            namespace: io.kestra.unittest
            pluginDefaults:
              - type: io.kestra.plugin.core.debug.Return
                values:
                  format: It's alive!
            tasks:
              - id: template
                type: io.kestra.plugin.core.templating.TemplatedTask
                spec: |
                  type: io.kestra.plugin.core.debug.Return
            """);

        // When
        Output output = templatedTask.run(runContextFactory.of());

        // Then
        assertThat(((Return.Output) output).getValue()).isEqualTo("It's alive!");
    }

    @Test
    void shouldKeepSpecValueWhenSpecSetsPropertyGivenNonForcedPluginDefault() throws Exception {
        // Given
        TemplatedTask templatedTask = templatedTaskOf("""
            id: templated-defaults
            namespace: io.kestra.unittest
            pluginDefaults:
              - type: io.kestra.plugin.core.debug.Return
                values:
                  format: It's alive!
            tasks:
              - id: template
                type: io.kestra.plugin.core.templating.TemplatedTask
                spec: |
                  type: io.kestra.plugin.core.debug.Return
                  format: It's mine!
            """);

        // When
        Output output = templatedTask.run(runContextFactory.of());

        // Then
        assertThat(((Return.Output) output).getValue()).isEqualTo("It's mine!");
    }

    @Test
    void shouldOverrideSpecValueWhenSpecSetsPropertyGivenForcedPluginDefault() throws Exception {
        // Given
        TemplatedTask templatedTask = templatedTaskOf("""
            id: templated-defaults
            namespace: io.kestra.unittest
            pluginDefaults:
              - type: io.kestra.plugin.core.debug.Return
                forced: true
                values:
                  format: It's alive!
            tasks:
              - id: template
                type: io.kestra.plugin.core.templating.TemplatedTask
                spec: |
                  type: io.kestra.plugin.core.debug.Return
                  format: It's mine!
            """);

        // When
        Output output = templatedTask.run(runContextFactory.of());

        // Then
        assertThat(((Return.Output) output).getValue()).isEqualTo("It's alive!");
    }

    /**
     * The flow reported in ticket #2253: a plain task and a templated task of the same type must both pick up the
     * default. The report used a namespace-level default, declared on the flow here — both are resolved into the
     * same list, so the templated task sees them identically.
     */
    @Test
    void shouldLogSameMessageWhenPlainAndTemplatedTaskRunGivenPluginDefaultForTaskType() throws Exception {
        // Given
        FlowWithSource flow = flowOf("""
            id: test_defaults
            namespace: company.team

            pluginDefaults:
              - type: io.kestra.plugin.core.log.Log
                values:
                  message: "{{ vars.message }} xyz"

            variables:
              message: abc

            tasks:
              - id: test1
                type: io.kestra.plugin.core.log.Log
              - id: test2
                type: io.kestra.plugin.core.templating.TemplatedTask
                spec: |
                  type: io.kestra.plugin.core.log.Log
            """);

        Log plain = (Log) flow.getTasks().getFirst();
        TemplatedTask templated = (TemplatedTask) flow.getTasks().get(1);

        // the expectation is the plain task's own defaulted message rather than a literal, so the test does not also
        // pin down which default level wins: the core test configuration declares a global default for this type.
        String expected = runContextOf(flow, plain).render((String) plain.getMessage());
        assertThat(expected).isNotBlank();

        // When
        List<String> logged = capturingFlowLogs(() ->
        {
            plain.run(runContextOf(flow, plain));
            templated.run(runContextOf(flow, templated));
        });

        // Then - the invariant the report asks for: the templated task logs exactly what the plain task logs
        assertThat(logged.stream().filter(expected::equals).count()).isEqualTo(2);
    }

    @Test
    void shouldApplyDefaultWhenSpecDeclaresPluginDefaultsRefGivenNamedPluginDefault() throws Exception {
        // Given
        TemplatedTask templatedTask = templatedTaskOf("""
            id: templated-defaults
            namespace: io.kestra.unittest
            pluginDefaults:
              - type: io.kestra.plugin.core.debug.Return
                ref: alive
                values:
                  format: It's alive!
            tasks:
              - id: template
                type: io.kestra.plugin.core.templating.TemplatedTask
                spec: |
                  type: io.kestra.plugin.core.debug.Return
                  pluginDefaultsRef: alive
            """);

        // When
        Output output = templatedTask.run(runContextFactory.of());

        // Then
        assertThat(((Return.Output) output).getValue()).isEqualTo("It's alive!");
    }

    @Test
    void shouldFailValidationWhenSpecOmitsRequiredPropertyGivenNoPluginDefault() {
        // Given
        RunContext runContext = runContextFactory.of();
        TemplatedTask templatedTask = TemplatedTask.builder()
            .id("template")
            .type(TemplatedTask.class.getName())
            .spec(Property.ofValue("type: io.kestra.plugin.core.http.Download"))
            .build();

        // When
        var exception = assertThrows(ConstraintViolationException.class, () -> templatedTask.run(runContext));

        // Then
        assertThat(exception.getMessage()).contains("uri");
    }

    /**
     * The single templated task of the given flow, parsed the way the runtime does: plugin defaults are resolved
     * while the flow is parsed, so the whole path must be exercised for them to reach the task. The task is
     * serialized and read back, as it is when the executor hands it to a worker.
     */
    private TemplatedTask templatedTaskOf(String source) throws Exception {
        FlowWithSource flow = flowOf(source);

        ObjectMapper mapper = JacksonMapper.ofJson();
        return (TemplatedTask) mapper.readValue(mapper.writeValueAsString(flow.getTasks().getFirst()), Task.class);
    }

    private FlowWithSource flowOf(String source) throws Exception {
        String tenant = TestsUtils.randomTenant(TemplatedTaskTest.class.getSimpleName());
        return pluginDefaultService.injectAllDefaults(GenericFlow.fromYaml(tenant, source), true);
    }

    /**
     * A run context as the executor builds it, so that the flow's own variables resolve and the task logs under the
     * 'flow' logger {@link #capturingFlowLogs(ThrowingRunnable)} listens on.
     */
    private RunContext runContextOf(FlowWithSource flow, Task task) {
        Execution execution = TestsUtils.mockExecution(flow, Map.of(), null);
        return runContextFactory.of(flow, task, execution, TestsUtils.mockTaskRun(execution, task));
    }

    /** Runs the given tasks and returns what they logged. */
    private static List<String> capturingFlowLogs(ThrowingRunnable runnable) throws Exception {
        Logger flowLogger = (Logger) LoggerFactory.getLogger("flow");
        List<String> messages = new ArrayList<>();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                messages.add(event.getFormattedMessage());
            }
        };
        appender.setContext(flowLogger.getLoggerContext());
        appender.start();
        flowLogger.addAppender(appender);

        try {
            runnable.run();
        } finally {
            flowLogger.detachAppender(appender);
        }

        return messages;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

}