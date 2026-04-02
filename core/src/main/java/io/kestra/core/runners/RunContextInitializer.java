package io.kestra.core.runners;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.Lists;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.plugins.PluginConfigurations;
import io.kestra.core.services.NamespaceService;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * This class is responsible to initialize and hydrate a {@link RunContext} for a specific run context.
 */
@Singleton
public class RunContextInitializer {

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected PluginConfigurations pluginConfigurations;

    @Inject
    protected RunContextLoggerFactory contextLoggerFactory;

    @Inject
    protected StorageInterface storageInterface;

    @Inject
    protected NamespaceFactory namespaceFactory;

    @Inject
    protected NamespaceService namespaceService;

    @Value("${kestra.encryption.secret-key}")
    protected Optional<String> secretKey;

    @Inject
    protected RunContextCache runContextCache;

    @Value("${kestra.environment.name}")
    @Nullable
    protected String kestraEnvironment;

    @Value("${kestra.url}")
    @Nullable
    protected String kestraUrl;

    /**
     * Initializes the given {@link RunContext} for the given {@link WorkerTask} for executor.
     *
     * @param runContext The runContext to initialize.
     * @return The initialized runContext
     */
    public DefaultRunContext forExecutor(final DefaultRunContext runContext) {
        runContext.init(applicationContext);

        return runContext;
    }

    /**
     * Builds a {@link RunContext} for the given {@link WorkerTask} on the worker side.
     * <p>
     * Reconstructs the full variables map from {@link WorkerTaskData} plus locally available
     * state (task, taskrun, envs, globals, kestra config, secret consumer).
     *
     * @param workerTask The {@link WorkerTask} received from the wire.
     * @return a fully initialized RunContext ready for task execution
     */
    public DefaultRunContext forWorker(final WorkerTask workerTask) {
        return forWorker(workerTask, Function.identity(), null);
    }

    /**
     * Builds a {@link RunContext} for the given {@link WorkerTask} for a WorkingDirectory task.
     * <p>
     * Preserves the current taskrun as {@code workerTaskrun} before overwriting with the subtask's taskrun.
     *
     * @param workerTask The {@link WorkerTask}.
     * @return a fully initialized RunContext
     */
    public DefaultRunContext forWorkingDirectory(final WorkerTask workerTask) {
        return forWorker(workerTask, variables ->
        {
            variables.put("workerTaskrun", variables.get("taskrun"));
            return variables;
        }, null);
    }

    /**
     * Builds a {@link RunContext} for a subtask within a WorkingDirectory, reusing the parent's
     * {@link WorkingDir} so that namespace files and prior subtask outputs remain accessible.
     *
     * @param workerTask The subtask's {@link WorkerTask}.
     * @param workingDir The parent WorkingDirectory's {@link WorkingDir}.
     * @return a fully initialized RunContext sharing the parent's working directory
     */
    public DefaultRunContext forWorkingDirectorySubtask(final WorkerTask workerTask, final WorkingDir workingDir) {
        return forWorker(workerTask, variables ->
        {
            variables.put("workerTaskrun", variables.get("taskrun"));
            return variables;
        }, workingDir);
    }

    @SuppressWarnings("unchecked")
    private DefaultRunContext forWorker(final WorkerTask workerTask,
        final Function<Map<String, Object>, Map<String, Object>> variablesModifier,
        final WorkingDir workingDir) {
        final Task task = workerTask.getTask();
        final TaskRun taskRun = workerTask.getTaskRun();
        final WorkerTaskData data = workerTask.getData();

        // Reconstruct full variables from wire data + locally available state
        Map<String, Object> variables = new HashMap<>(data.variables());
        variables.put("task", RunVariables.of(task));
        variables.put("taskrun", RunVariables.of(taskRun));
        enrichVariablesWithWorkerState(variables);

        // Handle workerTaskrun value propagation (for WorkingDirectory subtasks)
        Map<String, Object> workerTaskRun = (Map<String, Object>) variables.get("workerTaskrun");
        if (workerTaskRun != null && workerTaskRun.containsKey("value")) {
            Map<String, Object> taskrun = new HashMap<>((Map<String, Object>) variables.get("taskrun"));
            taskrun.put("value", workerTaskRun.get("value"));
            variables.put("taskrun", taskrun);
        }

        // Rehydrate outputs (EE override point)
        Object outputs = variables.getOrDefault("outputs", Map.of());
        if (outputs instanceof Map) {
            variables.put("outputs", rehydrateOutputs((Map<String, Object>) outputs));
        }

        final RunContextLogger runContextLogger = contextLoggerFactory.create(workerTask);
        addSecretConsumer(variables, runContextLogger);

        variables = variablesModifier.apply(variables);

        DefaultRunContext runContext = buildAndInitRunContext(variables, data.secretInputs(), data.traceParent(), workingDir);
        runContext.setPluginConfiguration(pluginConfigurations.getConfigurationByPluginTypeOrAliases(task.getType(), task.getClass()));
        runContext.setStorage(new InternalStorage(runContextLogger.logger(), StorageContext.forTask(taskRun), storageInterface, namespaceService, namespaceFactory));
        runContext.setLogger(runContextLogger);
        runContext.setTask(task);

        return runContext;
    }

    /**
     * Builds the kestra configuration map from local worker config values.
     */
    private Map<String, String> buildKestraConfig() {
        Map<String, String> kestra = HashMap.newHashMap(2);
        if (kestraEnvironment != null) {
            kestra.put("environment", kestraEnvironment);
        }
        if (kestraUrl != null) {
            kestra.put("url", kestraUrl);
        }
        return kestra;
    }

    /**
     * Rehydrate outputs from internal storage if enabled.
     * As outputs in internal storage is an EE feature, this is a no-op in OSS.
     */
    protected Map<String, Object> rehydrateOutputs(Map<String, Object> outputs) {
        return outputs;
    }

    /**
     * Initializes the given {@link RunContext} for the given {@link WorkerTaskResult} and parent {@link TaskRun}.
     *
     * @param runContext The {@link RunContext} to initialize.
     * @param workerTaskResult The {@link WorkerTaskResult}.
     * @param parent The parent {@link TaskRun}.
     * @return The {@link RunContext} to initialize
     */
    @SuppressWarnings("unchecked")
    public DefaultRunContext forWorker(final DefaultRunContext runContext,
        final WorkerTaskResult workerTaskResult,
        final TaskRun parent) {
        Map<String, Object> variables = new HashMap<>(runContext.getVariables());
        variables.put(RunVariables.ENVS, runContextCache.getEnvVars()); // inject local worker env vars

        Map<String, Object> outputs = variables.containsKey("outputs") ? new HashMap<>((Map<String, Object>) variables.get("outputs")) : new HashMap<>();

        Map<String, Object> triggerOutputs = variables.containsKey("trigger") ? new HashMap<>((Map<String, Object>) variables.get("trigger")) : new HashMap<>();

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> current = result;

        if (variables.containsKey("parents")) {
            for (Map<String, Map<String, String>> t : Lists.reverse((List<Map<String, Map<String, String>>>) variables.get("parents"))) {
                if (t.get("taskrun") != null && t.get("taskrun").get("value") != null) {
                    HashMap<String, Object> item = new HashMap<>();
                    current.put(t.get("taskrun").get("value"), item);
                    current = item;
                }
            }
        }

        if (parent.getValue() != null) {
            HashMap<String, Object> item = new HashMap<>();
            current.put(parent.getValue(), item);
            current = item;
        }

        if (workerTaskResult.getOutputs() != null) {
            current.putAll(workerTaskResult.getOutputs());
        }

        outputs.put(workerTaskResult.getTaskRun().getTaskId(), result);
        variables.put("outputs", new Secret(secretKey, runContext::logger).decrypt(outputs));
        variables.put("trigger", new Secret(secretKey, runContext::logger).decrypt(triggerOutputs));

        runContext.setVariables(variables);
        return runContext;
    }

    /**
     * Initializes the given {@link RunContext} for the given {@link TriggerContext} and {@link AbstractTrigger}.
     *
     * @param runContext The {@link RunContext} to initialize.
     * @param triggerContext The {@link TriggerContext}.
     * @param trigger The {@link AbstractTrigger}.
     * @return The {@link RunContext} to initialize
     */
    public DefaultRunContext forScheduler(final DefaultRunContext runContext,
        final TriggerContext triggerContext,
        final AbstractTrigger trigger) {

        runContext.init(applicationContext);

        final RunContextLogger runContextLogger = contextLoggerFactory.create(triggerContext, trigger);

        final Map<String, Object> variables = new HashMap<>(runContext.getVariables());
        addSecretConsumer(variables, runContextLogger);
        runContext.setVariables(variables);

        configureTrigger(runContext, runContextLogger, triggerContext, trigger);

        return runContext;
    }

    /**
     * Builds a {@link RunContext}, {@link TriggerContext}, and
     * {@link ConditionContext} for the given
     * {@link WorkerTrigger} on the worker side.
     *
     * @param workerTrigger The {@link WorkerTrigger} received from the wire.
     * @return the reconstructed TriggerContext and ConditionContext
     */
    public ConditionContext forWorker(final WorkerTrigger workerTrigger) {
        final WorkerTriggerData data = workerTrigger.getData();
        final AbstractTrigger trigger = workerTrigger.getTrigger();

        // Reconstruct variables from wire data + locally available state
        Map<String, Object> variables = new HashMap<>(data.variables());
        variables.put("flow", RunVariables.of(flow(data)));
        variables.put("labels", data.flowLabels() != null ? io.kestra.core.models.Label.toNestedMap(data.flowLabels()) : Map.of());
        enrichVariablesWithWorkerState(variables);

        final RunContextLogger runContextLogger = contextLoggerFactory.create(workerTrigger.triggerId(), trigger);
        addSecretConsumer(variables, runContextLogger);

        DefaultRunContext runContext = buildAndInitRunContext(variables, data.secretInputs(), data.traceParent(), null);
        configureTrigger(runContext, runContextLogger, workerTrigger.triggerId(), trigger);

        return ConditionContext.builder()
            .flow(flow(data))
            .runContext(runContext)
            .variables(data.conditionVariables())
            .build();
    }

    /**
     * Adds the secret consumer to the variables map, wiring it to the given logger.
     */
    private static void addSecretConsumer(Map<String, Object> variables, RunContextLogger logger) {
        variables.put(RunVariables.SECRET_CONSUMER_VARIABLE_NAME, (Consumer<String>) logger::usedSecret);
    }

    /**
     * Enriches the given variables map with locally available worker state:
     * environment variables, global variables, and kestra configuration.
     */
    private void enrichVariablesWithWorkerState(Map<String, Object> variables) {
        variables.put("envs", runContextCache.getEnvVars());
        variables.put("globals", runContextCache.getGlobalVars());
        variables.put("kestra", buildKestraConfig());
    }

    /**
     * Builds a new {@link DefaultRunContext} from the given variables and secret inputs,
     * initializes it with the application context, and sets the trace parent.
     *
     * @param workingDir optional working directory to reuse (e.g. from a parent WorkingDirectory task);
     *        when non-null, {@code init()} will keep it instead of creating a new one.
     */
    private DefaultRunContext buildAndInitRunContext(Map<String, Object> variables,
        List<String> secretInputs,
        String traceParent,
        WorkingDir workingDir) {
        var builder = new DefaultRunContext.Builder()
            .withVariables(variables)
            .withSecretInputs(secretInputs);
        if (workingDir != null) {
            builder = builder.withWorkingDir(workingDir);
        }
        DefaultRunContext runContext = builder.build();
        runContext.init(applicationContext);
        runContext.setTraceParent(traceParent);
        return runContext;
    }

    /**
     * Configures the given {@link DefaultRunContext} for trigger execution.
     */
    private void configureTrigger(DefaultRunContext runContext, RunContextLogger runContextLogger, TriggerId triggerId, AbstractTrigger trigger) {
        final String triggerExecutionId = IdUtils.create();

        final StorageContext storageContext = StorageContext.forTrigger(
            triggerId.getTenantId(), triggerId.getNamespace(), triggerId.getFlowId(), triggerExecutionId, triggerId.getTriggerId()
        );

        runContext.setLogger(runContextLogger);
        runContext.setStorage(new InternalStorage(runContextLogger.logger(), storageContext, storageInterface, namespaceService, namespaceFactory));
        runContext.setPluginConfiguration(pluginConfigurations.getConfigurationByPluginTypeOrAliases(trigger.getType(), trigger.getClass()));
        runContext.setTriggerExecutionId(triggerExecutionId);
        runContext.setTrigger(trigger);
    }

    /**
     * Reconstructs a minimal {@link io.kestra.core.models.flows.GenericFlow} from
     * {@link WorkerTriggerData} fields.
     */
    private static io.kestra.core.models.flows.GenericFlow flow(WorkerTriggerData data) {
        return io.kestra.core.models.flows.GenericFlow.builder()
            .id(data.flowId())
            .namespace(data.namespace())
            .tenantId(data.tenantId())
            .revision(data.flowRevision())
            .variables(data.flowVariables())
            .labels(data.flowLabels())
            .build();
    }
}
