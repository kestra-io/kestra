package io.kestra.core.runners.pebble.functions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.WebhookService;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Pebble function that synchronously runs a subflow and returns its terminal {@link Execution}, so
 * callers can read the subflow's flow-level outputs (e.g. {@code subflow(...).outputs.my_output}).
 * <p>
 * Primary use case: populating a SELECT/MULTISELECT input's {@code values:} at execute-form render
 * time. The spawned execution is tagged {@link ExecutionKind#SUBFLOW_FUNCTION} so it stays out of the
 * main execution list/dashboards.
 * <p>
 * <b>Limitations.</b> The call blocks until the subflow terminates, so it must run on a
 * blocking-friendly thread (input rendering on the webserver IO pool). Using it inside a Subflow task
 * property rendered on a worker is unsupported: a worker thread blocking on a child execution can
 * deadlock when worker slots are exhausted. Recursion (a subflow whose own inputs call
 * {@code subflow()}) is guarded best-effort by a per-thread depth cap.
 */
@Slf4j
@Singleton
public class SubflowFunction implements KestraFunction {
    public static final String NAME = "subflow";

    private static final String NAMESPACE_ARG = "namespace";
    private static final String ID_ARG = "id";
    private static final String INPUTS_ARG = "inputs";
    private static final String REVISION_ARG = "revision";

    /**
     * Guards against runaway recursion: a subflow's own inputs may call {@code subflow()}, which is
     * rendered synchronously on this same thread before any blocking wait. Incremented on entry and
     * decremented in a finally block.
     */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    @Inject
    private SubflowFunctionConfiguration configuration;

    // Late injection to avoid a circular dependency between Extension and these beans (cf. HttpFunction).
    @Inject
    private Provider<FlowMetaStoreInterface> flowMetaStore;

    @Inject
    private Provider<FlowInputOutput> flowInputOutput;

    @Inject
    private Provider<WebhookService> webhookService;

    @Override
    public List<String> getArgumentNames() {
        return List.of(NAMESPACE_ARG, ID_ARG, INPUTS_ARG, REVISION_ARG);
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put(NAMESPACE_ARG, "'company.team'");
        defaults.put(ID_ARG, "'my_subflow'");
        defaults.put(INPUTS_ARG, "{'my_input': 'my_value'}");
        defaults.put(REVISION_ARG, null);
        return defaults;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        String namespace = (String) args.get(NAMESPACE_ARG);
        String id = (String) args.get(ID_ARG);
        if (namespace == null || id == null) {
            throw new PebbleException(null, "The 'subflow' function expects the arguments 'namespace' and 'id'.", lineNumber, self.getName());
        }

        Map<String, String> flow = (Map<String, String>) context.getVariable("flow");
        if (flow == null) {
            throw new PebbleException(null, "The 'subflow' function can only be used in a flow context (e.g. an input's 'values'); the caller flow could not be resolved.", lineNumber, self.getName());
        }
        String tenantId = flow.get("tenantId");
        String callerNamespace = flow.get(NAMESPACE_ARG);
        String callerId = flow.get("id");

        Optional<Integer> revision = Optional.ofNullable(args.get(REVISION_ARG)).map(r -> ((Number) r).intValue());
        Map<String, Object> rawInputs = (Map<String, Object>) args.get(INPUTS_ARG);
        Map<String, Object> inputs = rawInputs != null ? rawInputs : Map.of();

        int depth = DEPTH.get();
        if (depth >= configuration.maxDepth()) {
            throw new PebbleException(null, "The 'subflow' function exceeded the maximum nesting depth of " + configuration.maxDepth()
                + " (a subflow's inputs likely call subflow() recursively).", lineNumber, self.getName());
        }

        DEPTH.set(depth + 1);
        try {
            FlowInterface targetFlow = flowMetaStore.get()
                .findByIdFromTask(tenantId, namespace, id, revision, tenantId, callerNamespace, callerId)
                .orElseThrow(() -> new PebbleException(null, "Unable to find flow '" + namespace + "'.'" + id + "'"
                    + revision.map(r -> " with revision " + r).orElse("") + ".", lineNumber, self.getName()));

            if (targetFlow instanceof FlowWithException fwe) {
                throw new PebbleException(null, "Cannot run the invalid flow '" + namespace + "'.'" + id + "': " + fwe.getException(), lineNumber, self.getName());
            }
            if (targetFlow.isDisabled()) {
                throw new PebbleException(null, "Cannot run the disabled flow '" + namespace + "'.'" + id + "'.", lineNumber, self.getName());
            }

            Execution execution;
            try {
                execution = Execution.newExecution(
                    targetFlow,
                    (f, e) -> flowInputOutput.get().readExecutionInputs(f, e, inputs),
                    List.of(new Label(Label.FROM, NAME)),
                    Optional.empty(),
                    ExecutionKind.SUBFLOW_FUNCTION
                );
            } catch (Exception e) {
                throw new PebbleException(e, "Invalid inputs for subflow '" + namespace + "'.'" + id + "': " + e.getMessage(), lineNumber, self.getName());
            }

            Execution terminated;
            try {
                terminated = webhookService.get().runAndWait(execution, (Flow) targetFlow, configuration.completionTimeout());
            } catch (Exception e) {
                throw new PebbleException(e, "Failed to run subflow '" + namespace + "'.'" + id + "': " + e.getMessage(), lineNumber, self.getName());
            }

            State.Type state = terminated.getState().getCurrent();
            if (state != State.Type.SUCCESS && state != State.Type.WARNING) {
                throw new PebbleException(null, "Subflow '" + namespace + "'.'" + id + "' ended in state " + state
                    + " (execution " + terminated.getId() + ").", lineNumber, self.getName());
            }

            return terminated;
        } finally {
            int current = DEPTH.get() - 1;
            if (current <= 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(current);
            }
        }
    }
}
