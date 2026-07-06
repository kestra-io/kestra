package io.kestra.executor.testkit;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.property.PropertyContext;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.RunVariables;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.services.TaskOutputService;

/**
 * {@link RunContextFactory} for executor unit tests: builds {@link DefaultRunContext}s with a real
 * {@link VariableRenderer} (real Pebble) but without a running Micronaut container — the
 * {@link io.micronaut.context.ApplicationContext} is a mocked bean locator answering only the beans
 * that {@code DefaultRunContext.services()} and {@code inputAndOutput()} resolve.
 * <p>
 * Only the overloads used by the executor are overridden; calling any other factory method
 * fails because the inherited injected fields are null by design.
 */
public class KitRunContextFactory extends RunContextFactory {
    private final VariableRenderer renderer;
    private final RunContextLoggerFactory loggerFactory;
    private final MetricRegistry meterRegistry;
    private final TaskOutputService taskOutputService;
    private final io.micronaut.context.ApplicationContext applicationContext;

    public KitRunContextFactory(
        VariableRenderer renderer,
        RunContextLoggerFactory loggerFactory,
        MetricRegistry meterRegistry,
        TaskOutputService taskOutputService,
        io.micronaut.context.ApplicationContext applicationContext) {
        this.renderer = renderer;
        this.loggerFactory = loggerFactory;
        this.meterRegistry = meterRegistry;
        this.taskOutputService = taskOutputService;
        this.applicationContext = applicationContext;
    }

    @Override
    public RunContext of(FlowInterface flow, Execution execution, Function<RunVariables.Builder, RunVariables.Builder> runVariableModifier, boolean decryptVariables) {
        RunContextLogger runContextLogger = loggerFactory.create(execution);

        return newKitBuilder(runContextLogger)
            .withVariables(
                runVariableModifier.apply(
                    new RunVariables.DefaultBuilder()
                        .withFlow(flow)
                        .withExecution(execution)
                        .withOutputs(taskOutputService.computeOutputs(execution))
                        .withDecryptVariables(decryptVariables)
                )
                    .build(runContextLogger, PropertyContext.create(renderer))
            )
            .build();
    }

    @Override
    public RunContext of(FlowInterface flow, Task task, Execution execution, TaskRun taskRun, boolean decryptVariables, VariableRenderer variableRenderer) {
        RunContextLogger runContextLogger = loggerFactory.create(taskRun, task, execution.getKind());

        return newKitBuilder(runContextLogger)
            .withTask(task)
            .withVariables(
                new RunVariables.DefaultBuilder()
                    .withFlow(flow)
                    .withTask(task)
                    .withExecution(execution)
                    .withOutputs(taskOutputService.computeOutputs(execution))
                    .withTaskRun(taskRun)
                    .withDecryptVariables(decryptVariables)
                    .build(runContextLogger, PropertyContext.create(renderer))
            )
            .build();
    }

    private DefaultRunContext.Builder newKitBuilder(RunContextLogger runContextLogger) {
        return new DefaultRunContext.Builder()
            .withApplicationContext(applicationContext)
            .withLogger(runContextLogger)
            .withMeterRegistry(meterRegistry)
            .withVariableRenderer(renderer)
            .withPluginConfiguration(Map.of())
            .withSecretInputs(List.of());
    }
}
