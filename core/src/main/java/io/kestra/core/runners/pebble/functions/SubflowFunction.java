package io.kestra.core.runners.pebble.functions;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.ExecutionService;

import io.micronaut.context.annotation.Requires;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Pebble function that synchronously runs a subflow and returns a small {@link Result} carrying its
 * terminal outputs/state/labels, so callers can read the subflow's flow-level outputs (e.g.
 * {@code subflow(...).outputs.my_output}).
 * <p>
 * Primary (and only supported) use case: populating a SELECT/MULTISELECT input's {@code values:} at
 * execute-form render time, which runs on the webserver IO pool.
 * <p>
 * <b>Where it may run.</b> The call blocks until the subflow terminates, so it is rejected outside a
 * flow-input render context: when a top-level {@code taskrun} or {@code trigger} variable is present
 * (task and trigger property rendering) the function throws, because those contexts may run on a
 * worker and a worker thread blocking on a child execution can deadlock when worker slots are
 * exhausted.
 * <p>
 * <b>Recursion.</b> A subflow whose own inputs call {@code subflow()} is bounded by a per-thread depth
 * cap. Input resolution is synchronous and runs on the same thread, so the cap catches both direct
 * self-recursion and mutual recursion across flows without a dedicated self-call check.
 * <p>
 * <b>Bean wiring.</b> The function is only registered on server types that render execute forms
 * ({@code WEBSERVER}, {@code STANDALONE}); on other server types the bean is absent and the function
 * is not exposed. {@link io.kestra.core.runners.pebble.Extension} injects it {@code @Nullable} and
 * skips registration when absent.
 */
@Slf4j
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public class SubflowFunction implements Function {
    public static final String NAME = "subflow";

    private static final String NAMESPACE_ARG = "namespace";
    private static final String ID_ARG = "id";
    private static final String INPUTS_ARG = "inputs";
    private static final String REVISION_ARG = "revision";
    private static final String LABELS_ARG = "labels";
    private static final String TIMEOUT_ARG = "timeout";

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
    private Provider<ExecutionService> executionService;

    @Override
    public List<String> getArgumentNames() {
        return List.of(NAMESPACE_ARG, ID_ARG, INPUTS_ARG, REVISION_ARG, LABELS_ARG, TIMEOUT_ARG);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        // The call blocks until the subflow terminates: only allow it where rendering happens on a
        // blocking-friendly thread (flow-input rendering on the webserver). A top-level 'taskrun' or
        // 'trigger' variable means we are rendering a task or trigger property, which may run on a worker.
        if (context.getVariable("taskrun") != null || context.getVariable("trigger") != null) {
            throw new PebbleException(null, "The 'subflow' function can only be used at flow-input render time (e.g. an input's 'values'); it is not supported inside task or trigger properties.", lineNumber, self.getName());
        }

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

        List<Label> labels = buildLabels(args.get(LABELS_ARG), self, lineNumber);
        Duration timeout = resolveTimeout(args.get(TIMEOUT_ARG), self, lineNumber);

        int depth = DEPTH.get();
        if (depth >= configuration.maxDepth()) {
            throw new PebbleException(null, "The 'subflow' function exceeded the maximum nesting depth of " + configuration.maxDepth()
                + " (a subflow's inputs likely call subflow() recursively).", lineNumber, self.getName());
        }

        DEPTH.set(depth + 1);
        try {
            // ACL is scoped to the caller flow (callerNamespace/callerId), matching the Subflow task trust model:
            // if the caller flow may reference the target, so may subflow(). Note this is reachable at execute-form
            // render time, not only at execution time, so anyone able to open the form triggers this resolution.
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
                    labels,
                    Optional.empty()
                );
            } catch (Exception e) {
                throw new PebbleException(e, "Invalid inputs for subflow '" + namespace + "'.'" + id + "': " + e.getMessage(), lineNumber, self.getName());
            }

            Execution terminated;
            try {
                terminated = executionService.get().runAndWait(execution, (Flow) targetFlow, timeout);
            } catch (Exception e) {
                throw new PebbleException(e, "Failed to run subflow '" + namespace + "'.'" + id + "': " + e.getMessage(), lineNumber, self.getName());
            }

            State.Type state = terminated.getState().getCurrent();
            if (state != State.Type.SUCCESS && state != State.Type.WARNING) {
                throw new PebbleException(null, "Subflow '" + namespace + "'.'" + id + "' ended in state " + state
                    + " (execution " + terminated.getId() + ").", lineNumber, self.getName());
            }

            return Result.of(terminated);
        } finally {
            int current = DEPTH.get() - 1;
            if (current <= 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(current);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Label> buildLabels(Object rawLabels, PebbleTemplate self, int lineNumber) {
        List<Label> labels = new ArrayList<>();
        if (rawLabels != null) {
            if (!(rawLabels instanceof Map)) {
                throw new PebbleException(null, "The 'subflow' function 'labels' must be a map of string keys to values.", lineNumber, self.getName());
            }
            ((Map<String, Object>) rawLabels).forEach((key, value) -> {
                if (value != null) {
                    // system labels are reserved for Kestra; only system.correlationId may be propagated by the caller
                    if (key.startsWith(Label.SYSTEM_PREFIX) && !key.equals(Label.CORRELATION_ID)) {
                        throw new PebbleException(null, "The 'subflow' function cannot set the system label '" + key + "'; system labels are reserved (except '" + Label.CORRELATION_ID + "').", lineNumber, self.getName());
                    }
                    labels.add(new Label(key, String.valueOf(value)));
                }
            });
        }
        // tag the execution as run by the subflow() function (cf. the Subflow task's system.from label)
        labels.add(new Label(Label.FROM, NAME));
        return labels;
    }

    private Duration resolveTimeout(Object rawTimeout, PebbleTemplate self, int lineNumber) {
        Duration timeout;
        if (rawTimeout == null) {
            timeout = configuration.defaultTimeout();
        } else if (rawTimeout instanceof Duration d) {
            timeout = d;
        } else if (rawTimeout instanceof String s) {
            try {
                timeout = Duration.parse(s);
            } catch (DateTimeParseException e) {
                throw new PebbleException(e, "The 'subflow' function 'timeout' must be an ISO-8601 duration (e.g. 'PT30S'), got '" + s + "'.", lineNumber, self.getName());
            }
        } else {
            throw new PebbleException(null, "The 'subflow' function 'timeout' must be an ISO-8601 duration string (e.g. 'PT30S').", lineNumber, self.getName());
        }

        if (timeout.compareTo(configuration.maxTimeout()) > 0) {
            throw new PebbleException(null, "The 'subflow' function 'timeout' (" + timeout + ") exceeds the maximum allowed (" + configuration.maxTimeout() + ").", lineNumber, self.getName());
        }
        return timeout;
    }

    /**
     * The minimal, navigable result returned to the template, instead of the full {@link Execution}
     * object (which exposes internal state callers should not depend on).
     *
     * @param id      the terminal execution id
     * @param state   the terminal state name (e.g. {@code SUCCESS})
     * @param outputs the subflow's flow-level outputs, navigable as {@code subflow(...).outputs.xxx}
     * @param labels  the execution labels as a {@code key -> value} map
     */
    public record Result(String id, String state, Map<String, Object> outputs, Map<String, String> labels) {
        static Result of(Execution execution) {
            Map<String, String> labels = new HashMap<>();
            execution.getLabels().forEach(label -> labels.put(label.key(), label.value()));

            return new Result(
                execution.getId(),
                execution.getState().getCurrent().name(),
                execution.getOutputs() != null ? execution.getOutputs() : Map.of(),
                labels
            );
        }
    }
}
