package io.kestra.webserver.services;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.event.Level;

import io.kestra.core.contexts.configuration.KestraConfiguration;
import io.kestra.core.debug.Breakpoint;
import io.kestra.core.executor.command.Create;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.FlowMetaStores;
import io.kestra.core.runners.ProcessedFlow;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.LabelService;
import io.kestra.core.utils.Logs;

import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.models.Label.CORRELATION_ID;

/**
 * Builds the command that creates a new execution of a flow, shared by every entry point that starts one on
 * behalf of a caller, so draft, validation and condition-check rules are applied the same way everywhere.
 */
@Slf4j
@Singleton
public class ExecutionCreationService {
    private final FlowService flowService;
    private final FlowRepositoryInterface flowRepository;
    private final FlowMetaStoreInterface flowMetaStore;
    private final ModelValidator modelValidator;
    private final Optional<OpenTelemetry> openTelemetry;
    private final KestraConfiguration kestraConfiguration;

    @Inject
    public ExecutionCreationService(
        final FlowService flowService,
        final FlowRepositoryInterface flowRepository,
        final FlowMetaStoreInterface flowMetaStore,
        final ModelValidator modelValidator,
        final Optional<OpenTelemetry> openTelemetry,
        final KestraConfiguration kestraConfiguration) {
        this.flowService = flowService;
        this.flowRepository = flowRepository;
        this.flowMetaStore = flowMetaStore;
        this.modelValidator = modelValidator;
        this.openTelemetry = openTelemetry;
        this.kestraConfiguration = kestraConfiguration;
    }

    /**
     * @param revision the revision to execute, or the latest published one when empty
     * @param kind the kind of execution to create, or null for a regular one
     * @throws NoSuchElementException if the flow or the revision does not exist
     * @throws IllegalArgumentException if the flow cannot be executed as requested
     */
    public ExecutableFlow findExecutableFlow(final String tenantId, final String namespace, final String id, final Optional<Integer> revision, @Nullable final ExecutionKind kind) {
        final Flow flow;
        try {
            flow = flowService.getFlowIfExecutableOrThrow(tenantId, namespace, id, revision);
        } catch (NoSuchElementException e) {
            // No non-draft revision was found. If the flow exists but only has draft revisions,
            // reject the request as unprocessable with an explanation instead of a bare 404, so the
            // user knows to save a published revision before executing without a revision.
            if (revision.isEmpty()) {
                flowRepository.findByIdWithoutAcl(tenantId, namespace, id, Optional.empty())
                    .filter(f -> !f.isDeleted() && f.isDraft())
                    .ifPresent(draftFlow ->
                    {
                        throw new IllegalArgumentException(
                            "Flow execution blocked: flow " + draftFlow.uid() + " only has draft revisions. Save it as a published revision before executing it without a revision."
                        );
                    });
            }
            throw e;
        }

        if (flow.isDraft()) {
            controlDraftExecutableAs(flow, kind);

            // Only drafts need re-validation here: published revisions are already validated at save
            // time, so re-validating every execution would just add cost to the hot path. A draft can be
            // saved with constraint violations, so when the user explicitly executes one (by passing its
            // revision) the request is rejected as unprocessable with the validation error, rather than
            // starting an execution that cannot run.
            Optional<ConstraintViolationException> violations = flowService.validateForExecution(flow);
            if (violations.isPresent()) {
                throw new IllegalArgumentException("Flow execution blocked: flow definition is invalid. " + violations.get().getMessage());
            }
        }

        // inputs render against the flow, and the labels, the executor will build the execution from,
        // so a `defaults` referencing a label the flow only carries at runtime resolves here too
        ProcessedFlow processedFlow = FlowMetaStores.findForRuntimeOrRaw(flowMetaStore, flow);
        Flow runtimeFlow = processedFlow.flow() instanceof FlowWithException ? flow : processedFlow.flow();
        return new ExecutableFlow(flow, runtimeFlow, processedFlow.pinnedLabelKeys());
    }

    /**
     * Validates the labels a caller asks a new execution to carry and defaults its origin to the API.
     *
     * @throws IllegalArgumentException if a label is a system label only Kestra may set
     */
    public List<Label> requestLabels(final List<Label> labels) {
        List<Label> parsedLabels = new ArrayList<>(labels);
        parsedLabels.forEach(modelValidator::validate);

        // system labels can only be set by Kestra itself; the only exceptions accepted from a
        // client are system.correlationId and system.from=ui (sent by the UI). Any other
        // system.from value (scheduler/trigger/worker/…) would let a caller spoof the origin.
        Optional<Label> first = parsedLabels.stream()
            .filter(
                label -> Label.isSystem(label.key())
                    && !label.key().equals(CORRELATION_ID)
                    && !(label.key().equals(Label.FROM) && Label.FromLabel.UI.value.equals(label.value()))
            )
            .findFirst();
        if (first.isPresent()) {
            throw new IllegalArgumentException("System labels can only be set by Kestra itself, offending label: " + first.get().key() + "=" + first.get().value());
        }

        // default the origin to "api" when the client didn't set it (the UI sends system.from=ui)
        if (parsedLabels.stream().noneMatch(l -> l.key().equals(Label.FROM))) {
            parsedLabels.add(new Label(Label.FROM, Label.FromLabel.API.value));
        }

        return parsedLabels;
    }

    /**
     * Evaluates the flow's condition checks against the typed inputs and builds the create command.
     *
     * @param otelContext the trace context to propagate to the execution, captured on the caller's thread
     * @throws IllegalArgumentException if a failed condition check blocks the execution
     */
    public Create createCommand(
        final ExecutableFlow executable,
        final String executionId,
        final List<Label> labels,
        final Map<String, Object> executionInputs,
        @Nullable final Instant scheduleDate,
        @Nullable final ExecutionKind kind,
        @Nullable final List<Breakpoint> breakpoints,
        final Context otelContext) {
        Flow flow = executable.flow();
        List<Check> failed = flowService.getFailedChecks(executable.runtimeFlow(), executionInputs);
        Check.Behavior behavior = Check.resolveBehavior(failed);
        if (Check.Behavior.BLOCK_EXECUTION.equals(behavior)) {
            throw new IllegalArgumentException(
                "Flow execution blocked: one or more condition checks evaluated to false."
                    + "\nFailed checks: " + failed.stream().map(Check::getMessage).collect(Collectors.joining(", "))
            );
        }

        Create createCommand = Create.of(new ExecutionId(flow.getTenantId(), flow.getNamespace(), flow.getId(), executionId, flow.getRevision()))
            .withLabels(labels)
            .withInputs(executionInputs)
            .withScheduleDate(scheduleDate)
            .withKind(kind)
            .withBreakpoints(breakpoints);

        if (Check.Behavior.FAIL_EXECUTION.equals(behavior)) {
            Logs.logExecutionId(createCommand.executionFullId(), log, Level.WARN, "Flow execution failed because one or more condition checks evaluated to false.");
            createCommand = createCommand.withStateType(State.Type.FAILED);
        }

        // inject the traceparent from the captured OTel context into the command so it's propagated to the execution
        Map<String, String> traceCarrier = new HashMap<>();
        openTelemetry
            .map(OpenTelemetry::getPropagators)
            .map(ContextPropagators::getTextMapPropagator)
            .ifPresent(propagator -> propagator.inject(otelContext, traceCarrier, Map::put));
        if (traceCarrier.containsKey("traceparent")) {
            createCommand = createCommand.withTraceParent(traceCarrier.get("traceparent"));
        }

        return createCommand;
    }

    /**
     * @return the link to the execution's page in the UI, relative when no Kestra URL is configured
     */
    public URI executionUrl(final ExecutionId executionId) {
        String baseUrl = Optional.ofNullable(kestraConfiguration.url()).map(url -> url.endsWith("/") ? url.substring(0, url.length() - 1) : url).orElse("");
        return URI.create(
            baseUrl + "/ui" + (executionId.tenantId() != null ? "/" + executionId.tenantId() : "")
                + "/executions/"
                + executionId.namespace() + "/"
                + executionId.flowId() + "/"
                + executionId.executionId()
        );
    }

    /**
     * Rejects executing a draft revision as anything but a {@link ExecutionKind#PLAYGROUND} execution,
     * whether the revision is passed explicitly (create) or inherited from a replayed execution.
     */
    public static void controlDraftExecutableAs(final Flow flow, @Nullable final ExecutionKind kind) {
        if (flow.isDraft() && ExecutionKind.PLAYGROUND != kind) {
            throw new IllegalArgumentException(
                "Flow execution blocked: revision " + flow.getRevision() + " of flow " + flow.uid() +
                    " is a draft. Draft revisions can only be executed as playground executions."
            );
        }
    }

    /**
     * A flow that may be executed, with the flow the executor will build the execution from.
     *
     * @param flow the requested revision of the flow
     * @param runtimeFlow the flow inputs and condition checks are evaluated against
     * @param pinnedLabelKeys the label keys the flow pins, which a caller cannot override
     */
    public record ExecutableFlow(Flow flow, Flow runtimeFlow, Set<String> pinnedLabelKeys) {
        public List<Label> contributedLabels(final List<Label> labels) {
            return LabelService.withoutPinned(labels, pinnedLabelKeys);
        }
    }
}
