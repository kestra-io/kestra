package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.SecretInput;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.models.triggers.AbstractTrigger;
import lombok.AllArgsConstructor;
import lombok.With;

import java.security.GeneralSecurityException;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Class for building {@link RunContext} variables.
 */
public final class RunVariables {

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
        builder
            .put("id", flow.getId())
            .put("namespace", flow.getNamespace())
            .put("revision", flow.getRevision()
            );

        if (flow.getTenantId() != null) {
            builder.put("tenantId", flow.getTenantId());
        }

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

        Builder withTask(Task task);

        Builder withExecution(Execution execution);

        Builder withTaskRun(TaskRun taskRun);

        Builder withDecryptVariables(boolean decryptVariables);

        Builder withVariables(Map<String, Object> variables);

        Builder withTrigger(AbstractTrigger trigger);

        Builder withEnvs(Map<String, ?> envs);

        Builder withGlobals(Map<?, ?> globals);

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
        protected Map<String, ?> envs;
        protected Map<?, ?> globals;
        private final Optional<String> secretKey;

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
                    builder.put("parent", parents.get(0));
                }
            }

            // Execution
            if (execution != null) {
                ImmutableMap.Builder<String, Object> executionMap = ImmutableMap.<String, Object>builder()
                    .put("id", execution.getId())
                    .put("startDate", execution.getState().getStartDate());

                if (execution.getOriginalId() != null) {
                    executionMap.put("originalId", execution.getOriginalId());
                }

                builder.put("execution", executionMap.build());

                if (execution.getTaskRunList() != null) {
                    Map<String, Object> outputs = new HashMap<>(execution.outputs());
                    if (decryptVariables) {
                        final Secret secret = new Secret(secretKey, logger);
                        outputs = secret.decrypt(outputs);
                    }
                    builder.put("outputs", outputs);
                }

                // Inputs
                Map<String, Object> inputs = new HashMap<>();
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
                }

                if (execution.getTrigger() != null && execution.getTrigger().getVariables() != null) {
                    builder.put("trigger", execution.getTrigger().getVariables());
                }

                if (execution.getLabels() != null) {
                    builder.put("labels", execution.getLabels()
                        .stream()
                        .filter(label -> label.value() != null && label.key() != null)
                        .map(label -> new AbstractMap.SimpleEntry<>(
                            label.key(),
                            label.value()
                        ))
                        // using an accumulator in case labels with the same key exists: the first is kept
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first))
                    );
                }

                if (execution.getVariables() != null) {
                    builder.putAll(execution.getVariables());
                }
            }

            // adds any additional variables
            if (variables != null) {
                builder.putAll(variables);
            }

            return builder.build();
        }
    }

    private RunVariables(){}
}
