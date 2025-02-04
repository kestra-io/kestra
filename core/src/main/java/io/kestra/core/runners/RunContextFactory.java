package io.kestra.core.runners;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginConfigurations;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Singleton
public class RunContextFactory {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected PluginConfigurations pluginConfigurations;

    @Inject
    protected VariableRenderer variableRenderer;

    @Inject
    protected StorageInterface storageInterface;

    @Inject
    protected FlowService flowService;

    @Inject
    protected MetricRegistry metricRegistry;

    @Inject
    protected RunContextCache runContextCache;

    @Inject
    protected WorkingDirFactory workingDirFactory;

    @Value("${kestra.encryption.secret-key}")
    protected Optional<String> secretKey;

    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    private KVStoreService kvStoreService;

    // hacky
    public RunContextInitializer initializer() {
        return applicationContext.getBean(RunContextInitializer.class);
    }

    public RunContext of(Flow flow, Execution execution) {
        return of(flow, execution, Function.identity());
    }

    public RunContext of(Flow flow, Execution execution, Function<RunVariables.Builder, RunVariables.Builder> runVariableModifier) {
        RunContextLogger runContextLogger = runContextLoggerFactory.create(execution);

        return newBuilder()
            // Logger
            .withLogger(runContextLogger)
            // Execution
            .withPluginConfiguration(Map.of())
            .withStorage(new InternalStorage(runContextLogger.logger(), StorageContext.forExecution(execution), storageInterface, flowService))
            .withVariables(runVariableModifier.apply(
                newRunVariablesBuilder()
                    .withFlow(flow)
                    .withExecution(execution)
                    .withDecryptVariables(true)
                    .withSecretInputs(secretInputsFromFlow(flow))
                )
                .build(runContextLogger))
            .withSecretInputs(secretInputsFromFlow(flow))
            .build();
    }

    public RunContext of(Flow flow, Task task, Execution execution, TaskRun taskRun) {
        return this.of(flow, task, execution, taskRun, true);
    }

    public RunContext of(Flow flow, Task task, Execution execution, TaskRun taskRun, boolean decryptVariables) {
        RunContextLogger runContextLogger = runContextLoggerFactory.create(taskRun, task);

        return newBuilder()
            // Logger
            .withLogger(runContextLogger)
            // Task
            .withPluginConfiguration(pluginConfigurations.getConfigurationByPluginTypeOrAliases(task.getType(), task.getClass()))
            .withStorage(new InternalStorage(runContextLogger.logger(), StorageContext.forTask(taskRun), storageInterface, flowService))
            .withVariables(newRunVariablesBuilder()
                .withFlow(flow)
                .withTask(task)
                .withExecution(execution)
                .withTaskRun(taskRun)
                .withDecryptVariables(decryptVariables)
                .withSecretInputs(secretInputsFromFlow(flow))
                .build(runContextLogger))
            .withKvStoreService(kvStoreService)
            .withSecretInputs(secretInputsFromFlow(flow))
            .withTask(task)
            .build();
    }

    public RunContext of(Flow flow, AbstractTrigger trigger) {
        RunContextLogger runContextLogger = runContextLoggerFactory.create(flow, trigger);
        return newBuilder()
            // Logger
            .withLogger(runContextLogger)
            // Task
            .withPluginConfiguration(pluginConfigurations.getConfigurationByPluginTypeOrAliases(trigger.getType(), trigger.getClass()))
            .withVariables(newRunVariablesBuilder()
                .withFlow(flow)
                .withTrigger(trigger)
                .withSecretInputs(secretInputsFromFlow(flow))
                .build(runContextLogger)
            )
            .withSecretInputs(secretInputsFromFlow(flow))
            .withTrigger(trigger)
            .build();
    }


    @VisibleForTesting
    public RunContext of(final Flow flow, final Map<String, Object> variables) {
        RunContextLogger runContextLogger = new RunContextLogger();
        return newBuilder()
            .withLogger(runContextLogger)
            .withStorage(new InternalStorage(runContextLogger.logger(), StorageContext.forFlow(flow), storageInterface, flowService))
            .withVariables(variables)
            .withSecretInputs(secretInputsFromFlow(flow))
            .build();
    }

    @VisibleForTesting
    public RunContext of(final Map<String, Object> variables) {
        return of((Task) null, variables);
    }

    @VisibleForTesting
    public RunContext of(final Task task, final Map<String, Object> variables) {
        RunContextLogger runContextLogger = new RunContextLogger();
        return newBuilder()
            .withLogger(runContextLogger)
            .withStorage(new InternalStorage(
                runContextLogger.logger(),
                new StorageContext() {
                    @Override
                    public URI getContextStorageURI() {
                        return URI.create("");
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public String getTenantId() {
                        var tenantId = ((Map<String, Object>)variables.getOrDefault("flow", Map.of())).get("tenantId");
                        return Optional.ofNullable(tenantId).map(Object::toString).orElse(null);
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public String getNamespace() {
                        var namespace = ((Map<String, Object>)variables.getOrDefault("flow", Map.of())).get("namespace");
                        return Optional.ofNullable(namespace).map(Object::toString).orElse(null);
                    }
                },
                storageInterface,
                flowService
            ))
            .withVariables(variables)
            .withTask(task)
            .build();
    }

    @VisibleForTesting
    public RunContext of() {
        return of(Map.of());
    }

    private List<String> secretInputsFromFlow(Flow flow) {
        if (flow == null || flow.getInputs() == null) {
            return Collections.emptyList();
        }

        return flow.getInputs().stream()
            .filter(input -> input.getType() == Type.SECRET)
            .map(input -> input.getId()).toList();
    }

    private DefaultRunContext.Builder newBuilder() {
        return new DefaultRunContext.Builder()
            // inject mandatory services and config
            .withApplicationContext(applicationContext) // TODO - ideally application should not be injected here
            .withMeterRegistry(metricRegistry)
            .withVariableRenderer(variableRenderer)
            .withStorageInterface(storageInterface)
            .withSecretKey(secretKey)
            .withWorkingDir(workingDirFactory.createWorkingDirectory())
            .withKvStoreService(kvStoreService);
    }

    protected RunVariables.Builder newRunVariablesBuilder() {
        return new RunVariables.DefaultBuilder(secretKey)
            .withEnvs(runContextCache.getEnvVars())
            .withGlobals(runContextCache.getGlobalVars());
    }
}
