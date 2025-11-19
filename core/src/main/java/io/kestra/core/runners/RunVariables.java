package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.input.SecretInput;
import io.kestra.core.models.property.PropertyContext;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.trigger.Schedule;
import lombok.AllArgsConstructor;
import lombok.With;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Class for building {@link RunContext} variables.
 */
public final class RunVariables {
    public static final String SECRET_CONSUMER_VARIABLE_NAME = "addSecretConsumer";
    public static final String FIXTURE_FILES_KEY = "io.kestra.datatype:test_fixtures_files";

    /**
     * Creates an immutable map representation of the given {@link Task}.
     *
     * @param task The TaskRun from which to create variables.
     * @return a new immutable {@link Map}.
     */
    static Map<String, Object> of(final Task task) {
        return Map.of(
            "id", task.getId(),
            "type", task.getType()
        );
    }

    /**
     * Creates an immutable map representation of the given {@link TaskRun}.
     *
     * @param taskRun The TaskRun from which to create variables.
     * @return a new immutable {@link Map}.
     */
    static Map<String, Object> of(final TaskRun taskRun) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
            .put("id", taskRun.getId())
            .put("startDate", taskRun.getState().getStartDate())
            .put("attemptsCount", taskRun.getAttempts() == null ? 0 : taskRun.getAttempts().size());

        if (taskRun.getParentTaskRunId() != null) {
            builder.put("parentId", taskRun.getParentTaskRunId());
        }

        if (taskRun.getValue() != null) {
            builder.put("value", taskRun.getValue());
        }

        if (taskRun.getIteration() != null) {
            builder.put("iteration", taskRun.getIteration());
        }

        return builder.build();
    }

    /**
     * Creates an immutable map representation of the given {@link FlowInterface}.
     *
     * @param flow The flow from which to create variables.
     * @return a new immutable {@link Map}.
     */
    static Map<String, Object> of(final FlowInterface flow) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        builder.put("id", flow.getId())
            .put("namespace", flow.getNamespace());

        Optional.ofNullable(flow.getRevision())
            .ifPresent(revision ->  builder.put("revision", revision));

        Optional.ofNullable(flow.getTenantId())
            .ifPresent(tenantId ->  builder.put("tenantId", tenantId));

        return builder.build();
    }

    /**
     * Creates an immutable map representation of the given {@link AbstractTrigger}.
     *
     * @param trigger The trigger from which to create variables.
     * @return a new immutable {@link Map}.
     */
    static Map<String, Object> of(final AbstractTrigger trigger) {
        return Map.of(
            "id", trigger.getId(),
            "type", trigger.getType()
        );
    }

    /**
     * Builder interface for construction run variables.
     */
    public interface Builder {

        Builder withFlow(FlowInterface flow);

        Builder withInputs(Map<String, Object> inputs);

        Builder withTask(Task task);

        Builder withExecution(Execution execution);

        Builder withTaskRun(TaskRun taskRun);

        Builder withDecryptVariables(boolean decryptVariables);

        Builder withVariables(Map<String, Object> variables);

        Builder withTrigger(AbstractTrigger trigger);

        Builder withEnvs(Map<String, ?> envs);

        Builder withGlobals(Map<?, ?> globals);

        Builder withSecretInputs(List<String> secretInputs);

        Builder withKestraConfiguration(KestraConfiguration kestraConfiguration);

        /**
         * Builds the immutable map of run variables.
         *
         * @param logger    The {@link RunContextLogger logger}
         * @return          The immutable map of variables.
         */
        Map<String, Object> build(RunContextLogger logger, PropertyContext propertyContext);
    }

    public record KestraConfiguration(String environment, String url) { }

    /**
     * Default builder class for constructing variables.
     */
    @AllArgsConstructor
    @With
    public static class DefaultBuilder implements RunVariables.Builder {

        protected FlowInterface flow;
        protected Task task;
        protected Execution execution;
        protected TaskRun taskRun;
        protected AbstractTrigger trigger;
        protected boolean decryptVariables = true;
        protected Map<String, Object> variables;
        protected Map<String, Object> inputs;
        protected Map<String, ?> envs;
        protected Map<?, ?> globals;
        private final Optional<String> secretKey;
        private List<String> secretInputs;
        private KestraConfiguration kestraConfiguration;

        public DefaultBuilder() {
            this(Optional.empty());
        }

        public DefaultBuilder(final Optional<String> secretKey) {
            this.secretKey = secretKey;
        }

        // Note: for performance reason, cloning maps should be avoided as much as possible.
        @Override
        public Map<String, Object> build(final RunContextLogger logger, final PropertyContext propertyContext) {
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

            builder.put("envs", envs != null ? envs : Map.of());
            builder.put("globals", globals != null ? globals : Map.of());

            // Flow
            if (flow != null) {
                builder.put("flow", RunVariables.of(flow));
            }

            // Task
            if (task != null) {
                builder.put("task", RunVariables.of(task));
            }

            // TaskRun
            if (taskRun != null) {
                builder.put("taskrun", RunVariables.of(taskRun));
            }

            // Trigger
            if (trigger != null) {
                builder.put("trigger", RunVariables.of(trigger));
            }

            // Parents
            if (taskRun != null && execution != null) {
                List<Map<String, Object>> parents = execution.parents(taskRun);
                builder.put("parents", parents);
                if (!parents.isEmpty()) {
                    builder.put("parent", parents.getFirst());
                }
            }

            // Execution
            if (execution != null) {
                ImmutableMap.Builder<String, Object> executionMap = ImmutableMap.builder();

                executionMap.put("id", execution.getId());

                if (execution.getState() != null) { // can occur in tests
                    executionMap.put("state", execution.getState().getCurrent());
                }

                Optional.ofNullable(execution.getState()).map(State::getStartDate)
                    .ifPresent(startDate -> executionMap.put("startDate", startDate));

                Optional.ofNullable(execution.getOriginalId())
                    .ifPresent(originalId -> executionMap.put("originalId", originalId));

                if (execution.getOutputs() != null) {
                    executionMap.put("outputs", execution.getOutputs());
                }

                builder.put("execution", executionMap.build());

                if (execution.getTaskRunList() != null) {
                    Map<String, Object> outputs = execution.outputs();
                    if (decryptVariables) {
                        final Secret secret = new Secret(secretKey, logger);
                        outputs = secret.decrypt(outputs);
                    }
                    builder.put("outputs", outputs);

                    Map<String, Object> tasksMap = new HashMap<>();

                    execution.getTaskRunList().forEach(taskRun -> {
                        if (taskRun.getState() != null) {
                            if (taskRun.getValue() == null) {
                                tasksMap.put(taskRun.getTaskId(), Map.of("state", taskRun.getState().getCurrent()));
                            } else {
                                if (tasksMap.containsKey(taskRun.getTaskId())) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> taskRunMap = (Map<String, Object>) tasksMap.get(taskRun.getTaskId());
                                    taskRunMap.put(taskRun.getValue(), Map.of("state", taskRun.getState().getCurrent()));
                                    tasksMap.put(taskRun.getTaskId(), taskRunMap);
                                } else {
                                    Map<String, Object> taskRunMap = new HashMap<>();
                                    taskRunMap.put(taskRun.getValue(), Map.of("state", taskRun.getState().getCurrent()));
                                    tasksMap.put(taskRun.getTaskId(), taskRunMap);
                                }
                            }
                        }
                    });

                    builder.put("tasks", tasksMap);
                }

                // Inputs
                Map<String, Object> inputs = this.inputs == null ? new HashMap<>() : new HashMap<>(this.inputs);
                if (execution.getInputs() != null) {
                    inputs.putAll(execution.getInputs());
                    if (decryptVariables && flow != null && flow.getInputs() != null) {
                        // if some inputs are of type secret, we decode them
                        final Secret secret = new Secret(secretKey, logger);
                        for (Input<?> input : flow.getInputs()) {
                            if (input instanceof SecretInput) {
                                decodeInput(secret, input.getId(), inputs);
                            }
                        }
                    }
                }

                if (flow != null && flow.getInputs() != null) {
                    // Create a new PropertyContext with 'flow' variables which are required by some pebble expressions.
                    PropertyContextWithVariables context = new PropertyContextWithVariables(propertyContext, Map.of("flow", RunVariables.of(flow)));

                    // we add default inputs value from the flow if not already set, this will be useful for triggers
                    flow.getInputs().stream()
                        .filter(input -> input.getDefaults() != null && !inputs.containsKey(input.getId()))
                        .forEach(input -> {
                            try {
                                inputs.put(input.getId(), FlowInputOutput.resolveDefaultValue(input, context));
                            } catch (IllegalVariableEvaluationException e) {
                                // Silent catch, if an input depends on another input, or a variable that is populated at runtime / input filling time, we can't resolve it here.
                            }
                        });
                }

                if (!inputs.isEmpty()) {
                    builder.put("inputs", inputs);

                    // if a secret input is used, add it to the list of secrets to mask on the logger
                    if (logger != null && !ListUtils.isEmpty(secretInputs)) {
                        for (String secretInput : secretInputs) {
                            String secret = (String) inputs.get(secretInput);
                            if (secret != null) {
                                logger.usedSecret(secret);
                            }
                        }
                    }
                }

                if (execution.getTrigger() != null && execution.getTrigger().getVariables() != null) {
                    builder.put("trigger", execution.getTrigger().getVariables());

                    // temporal hack to add back the `schedule`variables
                    // will be removed in 2.0
                    if (Schedule.class.getName().equals(execution.getTrigger().getType())) {
                        // add back its variables inside the `schedule` variables
                        builder.put("schedule", execution.getTrigger().getVariables());
                    }
                }

                if (execution.getLabels() != null) {
                    builder.put("labels", Label.toNestedMap(execution.getLabels()));
                }

                if (flow == null) {
                    FlowInterface flowFromExecution = GenericFlow.builder()
                        .id(execution.getFlowId())
                        .tenantId(execution.getTenantId())
                        .revision(execution.getFlowRevision())
                        .namespace(execution.getNamespace())
                        .build();
                    builder.put("flow", RunVariables.of(flowFromExecution));
                }
            }

            // variables
            Optional.ofNullable(execution)
                .map(Execution::getVariables)
                .or(() -> Optional.ofNullable(flow).map(FlowInterface::getVariables))
                .map(HashMap::new)
                .ifPresent(variables -> {
                    Object fixtureFiles = variables.remove(FIXTURE_FILES_KEY);
                    builder.put("vars", ImmutableMap.copyOf(variables));

                    if (fixtureFiles != null) {
                        builder.put("files", fixtureFiles);
                    }
                });

            // Kestra configuration
            if (kestraConfiguration != null) {
                Map<String, String> kestra = HashMap.newHashMap(2);
                if (kestraConfiguration.environment() != null) {
                    kestra.put("environment", kestraConfiguration.environment());
                }
                if (kestraConfiguration.url() != null) {
                    kestra.put("url", kestraConfiguration.url());
                }
                builder.put("kestra", kestra);
            }

            // adds any additional variables
            if (variables != null) {
                builder.putAll(variables);
            }

            if (logger != null && (variables == null || !variables.containsKey(RunVariables.SECRET_CONSUMER_VARIABLE_NAME))) {
                builder.put(RunVariables.SECRET_CONSUMER_VARIABLE_NAME, (Consumer<String>) logger::usedSecret);
            }

            return builder.build();
        }

        @SuppressWarnings("unchecked")
        private void decodeInput(Secret secret, String id, Map<String, Object> inputs) {
            // find the input value that can be nested in case the input has a '.' in it.
            if (id.indexOf('.') > -1) {
                String nestedId = id.substring(0, id.indexOf('.'));
                String restOfId = id.substring(id.indexOf('.') + 1);
                decodeInput(secret, restOfId, (Map<String, Object>) inputs.get(nestedId));
            } else if (inputs.containsKey(id)) {
                try {
                    String decoded = secret.decrypt(((String) inputs.get(id)));
                    inputs.put(id, decoded);
                } catch (GeneralSecurityException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private RunVariables(){}

    private record PropertyContextWithVariables(
        PropertyContext delegate,
        Map<String, Object> variables
    ) implements PropertyContext {

        @Override
        public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
            return delegate.render(inline, variables.isEmpty() ? this.variables : variables);
        }

        @Override
        public Map<String, Object> render(Map<String, Object> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
            return delegate.render(inline, variables.isEmpty() ? this.variables : variables);
        }
    }
}
