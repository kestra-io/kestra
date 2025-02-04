package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.input.SecretInput;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.utils.ListUtils;
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
     * Creates an immutable map representation of the given {@link Flow}.
     *
     * @param flow The flow from which to create variables.
     * @return a new immutable {@link Map}.
     */
    static Map<String, Object> of(final Flow flow) {
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
        return ImmutableMap.of(
            "id", trigger.getId(),
            "type", trigger.getType()
        );
    }

    /**
     * Builder interface for construction run variables.
     */
    public interface Builder {

        Builder withFlow(Flow flow);

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

        /**
         * Builds the immutable map of run variables.
         *
         * @param logger    The {@link RunContextLogger logger}
         * @return          The immutable map of variables.
         */
        Map<String, Object> build(final RunContextLogger logger);
    }

    /**
     * Default builder class for constructing variables.
     */
    @AllArgsConstructor
    @With
    public static class DefaultBuilder implements RunVariables.Builder {

        protected Flow flow;
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

        public DefaultBuilder() {
            this(Optional.empty());
        }

        public DefaultBuilder(final Optional<String> secretKey) {
            this.secretKey = secretKey;
        }

        @Override
        public Map<String, Object> build(final RunContextLogger logger) {
            ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

            builder.put("envs", envs != null ? envs : Map.of());
            builder.put("globals", globals != null ? globals : Map.of());

            // Flow
            if (flow != null) {
                builder.put("flow", RunVariables.of(flow));
                if (flow.getVariables() != null) {
                    builder.put("vars", flow.getVariables());
                }
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

                Optional.ofNullable(execution.getState()).map(State::getStartDate)
                    .ifPresent(startDate -> executionMap.put("startDate", startDate));

                Optional.ofNullable(execution.getOriginalId())
                    .ifPresent(originalId -> executionMap.put("originalId", originalId));

                builder.put("execution", executionMap.build());

                if (execution.getTaskRunList() != null) {
                    Map<String, Object> outputs = new HashMap<>(execution.outputs());
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
                                    Map<String, Object> taskRunMap = new HashMap<>((Map<String, Object>) tasksMap.get(taskRun.getTaskId()));
                                    taskRunMap.put(taskRun.getValue(), Map.of("state", taskRun.getState().getCurrent()));
                                    tasksMap.put(taskRun.getTaskId(), taskRunMap);
                                } else {
                                    tasksMap.put(taskRun.getTaskId(), Map.of(taskRun.getValue(), Map.of("state", taskRun.getState().getCurrent())));
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
                            if (input instanceof SecretInput && inputs.containsKey(input.getId())) {
                                try {
                                    String decoded = secret.decrypt(((String) inputs.get(input.getId())));
                                    inputs.put(input.getId(), decoded);
                                } catch (GeneralSecurityException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }

                if (flow != null && flow.getInputs() != null) {
                    // we add default inputs value from the flow if not already set, this will be useful for triggers
                    flow.getInputs().stream()
                        .filter(input -> input.getDefaults() != null && !inputs.containsKey(input.getId()))
                        .forEach(input -> inputs.put(input.getId(), input.getDefaults()));
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
                }

                if (execution.getLabels() != null) {
                    builder.put("labels", Label.toNestedMap(execution.getLabels()));
                }

                if (execution.getVariables() != null) {
                    builder.putAll(execution.getVariables());
                }

                if (flow == null) {
                    Flow flowFromExecution = Flow.builder()
                        .id(execution.getFlowId())
                        .tenantId(execution.getTenantId())
                        .revision(execution.getFlowRevision())
                        .namespace(execution.getNamespace())
                        .build();
                    builder.put("flow", RunVariables.of(flowFromExecution));
                }
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
    }

    private RunVariables(){}
}
