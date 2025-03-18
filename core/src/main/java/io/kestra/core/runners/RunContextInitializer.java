package io.kestra.core.runners;

import com.google.common.collect.Lists;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.plugins.PluginConfigurations;
import io.kestra.core.services.FlowService;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class is responsible to initialize and hydrate a {@link DefaultRunContext} for a specific run context.
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
    protected FlowService flowService;

    @Value("${kestra.encryption.secret-key}")
    protected Optional<String> secretKey;

    /**
     * Initializes the given {@link RunContext} for the given {@link Plugin}.
     *
     * @param runContext The {@link RunContext} to initialize.
     * @param plugin The {@link TaskRunner} used for initialization.
     * @return The {@link RunContext} to initialize
     */
    public DefaultRunContext forPlugin(final DefaultRunContext runContext,
                                       final Plugin plugin) {
        runContext.init(applicationContext);
        runContext.setPluginConfiguration(pluginConfigurations.getConfigurationByPluginTypeOrAliases(plugin.getType(), plugin.getClass()));
        return runContext;
    }

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
     * Initializes the given {@link RunContext} for the given {@link WorkerTask}.
     *
     * @param runContext The runContext to initialize.
     * @param workerTask The {@link WorkerTask}.
     * @return The initialized runContext
     */
    public DefaultRunContext forWorker(final DefaultRunContext runContext,
                                       final WorkerTask workerTask) {
        return forWorker(runContext, workerTask, Function.identity());
    }

    /**
     * Initializes the given {@link RunContext} for the given {@link WorkerTask}.
     *
     * @param runContext The runContext to initialize.
     * @param workerTask The {@link WorkerTask}.
     * @return The runContext to initialize
     */
    public DefaultRunContext forWorkingDirectory(final DefaultRunContext runContext,
                                                 final WorkerTask workerTask) {
        return forWorker(runContext, workerTask, variables -> {
            variables.put("workerTaskrun", variables.get("taskrun"));
            return variables;
        });
    }


    @SuppressWarnings("unchecked")
    private DefaultRunContext forWorker(final DefaultRunContext runContext,
                                        final WorkerTask workerTask,
                                        final Function<Map<String, Object>, Map<String, Object>> variablesModifier) {

        runContext.init(applicationContext);

        final Task task = workerTask.getTask();
        final TaskRun taskRun = workerTask.getTaskRun();

        // build new variables
        Map<String, Object> enrichedVariables = new HashMap<>(runContext.getVariables());
        enrichedVariables.put("taskrun", RunVariables.of(taskRun));
        enrichedVariables.put("task", RunVariables.of(task));

        Map<String, Object> workerTaskRun = (Map<String, Object>) enrichedVariables.get("workerTaskrun");
        if (workerTaskRun != null && workerTaskRun.containsKey("value")) {
            Map<String, Object> taskrun = new HashMap<>((Map<String, Object>) enrichedVariables.get("taskrun"));
            taskrun.put("value", workerTaskRun.get("value"));
            enrichedVariables.put("taskrun", taskrun);
        }

        final RunContextLogger runContextLogger = contextLoggerFactory.create(taskRun, task);
        enrichedVariables.put(RunVariables.SECRET_CONSUMER_VARIABLE_NAME, (Consumer<String>) runContextLogger::usedSecret);

        enrichedVariables = variablesModifier.apply(enrichedVariables);

        runContext.setVariables(enrichedVariables);
        runContext.setPluginConfiguration(pluginConfigurations.getConfigurationByPluginTypeOrAliases(task.getType(), task.getClass()));
        runContext.setStorage(new InternalStorage(runContextLogger.logger(), StorageContext.forTask(taskRun), storageInterface, flowService));
        runContext.setLogger(runContextLogger);
        runContext.setTask(task);

        return runContext;
    }

    /**
     * Initializes the given {@link RunContext} for the given {@link WorkerTaskResult} and parent {@link TaskRun}.
     *
     * @param runContext       The {@link RunContext} to initialize.
     * @param workerTaskResult The {@link WorkerTaskResult}.
     * @param parent           The parent {@link TaskRun}.
     * @return The {@link RunContext} to initialize
     */
    @SuppressWarnings("unchecked")
    public DefaultRunContext forWorker(final DefaultRunContext runContext,
                                       final WorkerTaskResult workerTaskResult,
                                       final TaskRun parent) {
        Map<String, Object> variables = new HashMap<>(runContext.getVariables());

        Map<String, Object> outputs = variables.containsKey("outputs") ?
            new HashMap<>((Map<String, Object>) variables.get("outputs")) :
            new HashMap<>();

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

        if (workerTaskResult.getTaskRun().getOutputs() != null) {
            current.putAll(workerTaskResult.getTaskRun().getOutputs());
        }

        outputs.put(workerTaskResult.getTaskRun().getTaskId(), result);
        variables.put("outputs", new Secret(secretKey, runContext::logger).decrypt(outputs));

        runContext.setVariables(variables);
        return runContext;
    }

    /**
     * Initializes the given {@link RunContext} for the given {@link TriggerContext} and {@link AbstractTrigger}.
     *
     * @param runContext     The {@link RunContext} to initialize.
     * @param triggerContext The {@link TriggerContext}.
     * @param trigger        The {@link AbstractTrigger}.
     * @return The {@link RunContext} to initialize
     */
    public DefaultRunContext forScheduler(final DefaultRunContext runContext,
                                          final TriggerContext triggerContext,
                                          final AbstractTrigger trigger) {

        runContext.init(applicationContext);

        final String triggerExecutionId = IdUtils.create();
        final RunContextLogger runContextLogger = contextLoggerFactory.create(triggerContext, trigger);

        final Map<String, Object> variables = new HashMap<>(runContext.getVariables());
        variables.put(RunVariables.SECRET_CONSUMER_VARIABLE_NAME, (Consumer<String>) runContextLogger::usedSecret);

        final StorageContext context = StorageContext.forTrigger(
            triggerContext.getTenantId(),
            triggerContext.getNamespace(),
            triggerContext.getFlowId(),
            triggerExecutionId,
            trigger.getId()
        );

        final InternalStorage storage = new InternalStorage(
            runContextLogger.logger(),
            context,
            storageInterface,
            flowService
        );

        runContext.setLogger(runContextLogger);
        runContext.setVariables(variables);
        runContext.setStorage(storage);
        runContext.setPluginConfiguration(pluginConfigurations.getConfigurationByPluginTypeOrAliases(trigger.getType(), trigger.getClass()));
        runContext.setTriggerExecutionId(triggerExecutionId);
        runContext.setTrigger(trigger);

        return runContext;
    }

    /**
     * Creates a new {@link RunContext} instance from a given {@link WorkerTrigger}.
     *
     * @param runContext    The {@link RunContext} to initialize.
     * @param workerTrigger The {@link WorkerTrigger}.
     * @return The {@link RunContext} to initialize
     */
    public RunContext forWorker(final DefaultRunContext runContext, final WorkerTrigger workerTrigger) {
        return forScheduler(
            runContext,
            workerTrigger.getTriggerContext(),
            workerTrigger.getTrigger()
        );
    }
}
