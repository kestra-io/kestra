package io.kestra.core.runners;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.property.PropertyContext;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginConfigurations;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.services.NamespaceService;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
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
    protected SecureVariableRendererFactory secureVariableRendererFactory;

    @Inject
    protected StorageInterface storageInterface;

    @Inject
    protected NamespaceService namespaceService;

    @Inject
    protected MetricRegistry metricRegistry;

    @Inject
    protected RunContextCache runContextCache;

    @Inject
    protected WorkingDirFactory workingDirFactory;

    @Value("${kestra.encryption.secret-key}")
    protected Optional<String> secretKey;

    @Value("${kestra.environment.name}")
    @Nullable
    protected String kestraEnvironment;

    @Value("${kestra.url}")
    @Nullable
    protected String kestraUrl;

    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    private KVStoreService kvStoreService;

    @Inject
    private NamespaceFactory namespaceFactory;

    // hacky
    public RunContextInitializer initializer() {
        return applicationContext.getBean(RunContextInitializer.class);
    }

    public RunContext of(FlowInterface flow, Execution execution) {
        return of(flow, execution, Function.identity());
    }

    public RunContext of(FlowInterface flow, Execution execution, boolean decryptVariable) {
        return of(flow, execution, Function.identity(), decryptVariable);
    }

    public RunContext of(FlowInterface flow, Execution execution, Function<RunVariables.Builder, RunVariables.Builder> runVariableModifier) {
        return of(flow, execution, runVariableModifier, true);
    }

    public RunContext of(FlowInterface flow, Execution execution, Function<RunVariables.Builder, RunVariables.Builder> runVariableModifier, boolean decryptVariables) {
        RunContextLogger runContextLogger = runContextLoggerFactory.create(execution);

        VariableRenderer variableRenderer = decryptVariables ? this.variableRenderer : secureVariableRendererFactory.createOrGet();

        return newBuilder()
            // Logger
            .withLogger(runContextLogger)
            // Execution
            .withPluginConfiguration(Map.of())
            .withStorage(new InternalStorage(runContextLogger.logger(), StorageContext.forExecution(execution), storageInterface, namespaceService, namespaceFactory))
            .withVariableRenderer(variableRenderer)
            .withVariables(runVariableModifier.apply(
                    newRunVariablesBuilder()
                        .withFlow(flow)
                        .withExecution(execution)
                        .withDecryptVariables(decryptVariables)
                        .withSecretInputs(secretInputsFromFlow(flow))
                )
                .build(runContextLogger, PropertyContext.create(variableRenderer)))
            .withSecretInputs(secretInputsFromFlow(flow))
            .build();
    }

    public RunContext of(FlowInterface flow, Task task, Execution execution, TaskRun taskRun) {
        return this.of(flow, task, execution, taskRun, true);
    }

    public RunContext of(FlowInterface flow, Task task, Execution execution, TaskRun taskRun, boolean decryptVariables) {
        return this.of(flow, task, execution, taskRun, decryptVariables, this.variableRenderer);
    }

    public RunContext of(FlowInterface flow, Task task, Execution execution, TaskRun taskRun, boolean decryptVariables, VariableRenderer variableRenderer) {
        RunContextLogger runContextLogger = runContextLoggerFactory.create(taskRun, task, execution.getKind());

        return newBuilder()
            // Logger
            .withLogger(runContextLogger)
            // Task
            .withPluginConfiguration(pluginConfigurations.getConfigurationByPluginTypeOrAliases(task.getType(), task.getClass()))
            .withStorage(new InternalStorage(runContextLogger.logger(), StorageContext.forTask(taskRun), storageInterface, namespaceService, namespaceFactory))
            .withVariables(newRunVariablesBuilder()
                .withFlow(flow)
                .withTask(task)
                .withExecution(execution)
                .withTaskRun(taskRun)
                .withDecryptVariables(decryptVariables)
                .withSecretInputs(secretInputsFromFlow(flow))
                .build(runContextLogger, PropertyContext.create(variableRenderer)))
            .withKvStoreService(kvStoreService)
            .withSecretInputs(secretInputsFromFlow(flow))
            .withTask(task)
            .withVariableRenderer(variableRenderer)
            .build();
    }

    public RunContext of(FlowInterface flow, AbstractTrigger trigger) {
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
                .build(runContextLogger, PropertyContext.create(this.variableRenderer))
            )
            .withSecretInputs(secretInputsFromFlow(flow))
            .withTrigger(trigger)
            .build();
    }

    public RunContext of(final FlowInterface flow, final Map<String, Object> variables) {
        RunContextLogger runContextLogger = new RunContextLogger();
        return newBuilder()
            .withLogger(runContextLogger)
            .withStorage(new InternalStorage(runContextLogger.logger(), StorageContext.forFlow(flow), storageInterface, namespaceService, namespaceFactory))
            .withVariables(newRunVariablesBuilder()
                .withFlow(flow)
                .withVariables(variables)
                .build(runContextLogger, PropertyContext.create(this.variableRenderer))
            )
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
                        return Optional.ofNullable(tenantId).map(Object::toString).orElse(MAIN_TENANT);
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public String getNamespace() {
                        var namespace = ((Map<String, Object>)variables.getOrDefault("flow", Map.of())).get("namespace");
                        return Optional.ofNullable(namespace).map(Object::toString).orElse(null);
                    }
                },
                storageInterface,
                namespaceService,
                namespaceFactory
            ))
            .withVariables(variables)
            .withTask(task)
            .build();
    }

    @VisibleForTesting
    public RunContext of() {
        return of(Map.of());
    }

    private List<String> secretInputsFromFlow(FlowInterface flow) {
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
            .withVariableRenderer(this.variableRenderer)
            .withStorageInterface(storageInterface)
            .withSecretKey(secretKey)
            .withWorkingDir(workingDirFactory.createWorkingDirectory())
            .withKvStoreService(kvStoreService);
    }

    protected RunVariables.Builder newRunVariablesBuilder() {
        return new RunVariables.DefaultBuilder(secretKey)
            .withEnvs(runContextCache.getEnvVars())
            .withGlobals(runContextCache.getGlobalVars())
            .withKestraConfiguration(new RunVariables.KestraConfiguration(kestraEnvironment, kestraUrl));
    }
}
