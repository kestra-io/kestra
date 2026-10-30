package io.kestra.webserver.services.playground;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.SecureVariableRendererFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ExecutionOutputService;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.TaskOutputService;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

/**
 * The Playground session of a user and a flow: their runs of it, what the next run builds on, and when a run ends.
 */
@Slf4j
@Singleton
public class PlaygroundService {
    public static final int RETAINED_RUNS = 10;
    public static final String LATEST = "LATEST";
    public static final String NOTHING = "NOTHING";

    /** The render context a task sees, without {@code envs}, {@code globals} and {@code kestra}, which describe the server rather than the run. */
    private static final Set<String> CONTEXT_KEYS = Set.of("flow", "task", "taskrun", "execution", "inputs", "outputs", "vars", "labels", "parents", "parent", "trigger", "item", "tasks");

    private static final int MAX_REUSE_DEPTH = RETAINED_RUNS * 2;

    private final ExecutionRepositoryInterface executionRepository;
    private final FlowRepositoryInterface flowRepository;
    private final FlowService flowService;
    private final TaskOutputRepositoryInterface taskOutputRepository;
    private final ExecutionService executionService;
    private final TaskOutputService taskOutputService;
    private final ExecutionOutputService executionOutputService;
    private final RunContextFactory runContextFactory;
    private final SecureVariableRendererFactory secureVariableRendererFactory;
    private final PlaygroundOwnerResolver ownerResolver;
    private final BroadcastQueueInterface<ExecutionKilled> killQueue;

    @Inject
    public PlaygroundService(
        ExecutionRepositoryInterface executionRepository,
        FlowRepositoryInterface flowRepository,
        FlowService flowService,
        TaskOutputRepositoryInterface taskOutputRepository,
        ExecutionService executionService,
        TaskOutputService taskOutputService,
        ExecutionOutputService executionOutputService,
        RunContextFactory runContextFactory,
        SecureVariableRendererFactory secureVariableRendererFactory,
        PlaygroundOwnerResolver ownerResolver,
        BroadcastQueueInterface<ExecutionKilled> killQueue) {
        this.executionRepository = Objects.requireNonNull(executionRepository);
        this.flowRepository = Objects.requireNonNull(flowRepository);
        this.flowService = Objects.requireNonNull(flowService);
        this.taskOutputRepository = Objects.requireNonNull(taskOutputRepository);
        this.executionService = Objects.requireNonNull(executionService);
        this.taskOutputService = Objects.requireNonNull(taskOutputService);
        this.executionOutputService = Objects.requireNonNull(executionOutputService);
        this.runContextFactory = Objects.requireNonNull(runContextFactory);
        this.secureVariableRendererFactory = Objects.requireNonNull(secureVariableRendererFactory);
        this.ownerResolver = Objects.requireNonNull(ownerResolver);
        this.killQueue = Objects.requireNonNull(killQueue);
    }

    /** The current caller's runs of the flow, newest first. */
    public ArrayListTotal<Execution> runs(String tenantId, String namespace, String flowId, int page, int size) {
        List<Execution> session = session(tenantId, namespace, flowId, ownerResolver.owner());
        int from = Math.min((page - 1) * size, session.size());
        return new ArrayListTotal<>(session.subList(from, Math.min(from + size, session.size())), session.size());
    }

    /**
     * The current caller's run of the flow with this id.
     *
     * @throws NoSuchElementException when it is not a Playground run of this flow, or belongs to someone else
     */
    public Execution run(String tenantId, String namespace, String flowId, String executionId) {
        return executionRepository.findById(tenantId, executionId)
            .filter(run -> ExecutionKind.PLAYGROUND == run.getKind())
            .filter(run -> namespace.equals(run.getNamespace()) && flowId.equals(run.getFlowId()))
            .filter(run -> ownerResolver.owner().map(owner -> owner.equals(owner(run).orElse(null))).orElse(true))
            .orElseThrow(() -> new NoSuchElementException("Unable to find the Playground run '%s' of flow '%s.%s'.".formatted(executionId, namespace, flowId)));
    }

    /**
     * @param buildOn {@code null} or {@link #LATEST} to build on the latest run, {@link #NOTHING} to start fresh, or a run id
     */
    public PlaygroundPlan plan(String tenantId, String namespace, String flowId, int revision, @Nullable String taskId, PlaygroundRunMode mode, @Nullable String buildOn) {
        Flow flow = flowService.getFlowIfExecutableOrThrow(tenantId, namespace, flowId, Optional.of(revision));
        if (NOTHING.equals(buildOn)) {
            PlaygroundPlan fresh = PlaygroundPlanner.plan(flow, taskId, mode, null, null);
            return taskId == null ? fresh : PlaygroundPlan.fresh(PlaygroundFreshReason.BUILD_ON_NOTHING, fresh.breakpoints());
        }

        Optional<Execution> previous = buildOn == null || LATEST.equals(buildOn)
            ? runs(tenantId, namespace, flowId, 1, 1).stream().findFirst()
            : Optional.of(run(tenantId, namespace, flowId, buildOn));
        Flow previousFlow = previous
            .flatMap(run -> flowRepository.findByIdWithoutAcl(tenantId, namespace, flowId, Optional.ofNullable(run.getFlowRevision())))
            .orElse(null);

        return PlaygroundPlanner.plan(flow, taskId, mode, previous.orElse(null), previousFlow);
    }

    /**
     * Ends the other runs of {@code newRun}'s user and flow, then deletes the terminated ones beyond the {@value #RETAINED_RUNS} newest.
     * A no-op for any other kind; failures are logged, never thrown, so they cannot fail the run that triggered them.
     */
    public void onNewRun(Execution newRun) {
        if (ExecutionKind.PLAYGROUND != newRun.getKind()) {
            return;
        }

        try {
            List<Execution> previousRuns = session(newRun.getTenantId(), newRun.getNamespace(), newRun.getFlowId(), owner(newRun))
                .stream()
                .filter(run -> !run.getId().equals(newRun.getId()))
                .toList();
            previousRuns.stream().filter(PlaygroundService::isKillable).forEach(this::kill);
            purge(newRun, previousRuns);
        } catch (Exception e) {
            log.warn("Unable to end the previous Playground runs of flow '{}.{}'.", newRun.getNamespace(), newRun.getFlowId(), e);
        }
    }

    /** Asks the executor to kill the run; the kill handler ignores a run that has meanwhile terminated. */
    public void kill(Execution run) {
        try {
            killQueue.emit(
                ExecutionKilledExecution.builder()
                    .state(ExecutionKilled.State.REQUESTED)
                    .executionId(run.getId())
                    .tenantId(run.getTenantId())
                    .isOnKillCascade(true)
                    .build()
            );
        } catch (QueueException e) {
            log.warn("Unable to kill the Playground run '{}'.", run.getId(), e);
        }
    }

    static boolean isKillable(Execution run) {
        State.Type state = run.getState().getCurrent();
        return !state.isTerminated() && State.Type.KILLING != state;
    }

    /**
     * For each task run {@code run} copied from the run it replayed, the task run that first produced it, following chained replays.
     * The logs and metrics of a reused task stay with that producer.
     */
    public Map<String, ReusedTaskRun> reused(Execution run) {
        Map<String, ReusedTaskRun> producers = new LinkedHashMap<>();
        Map<String, String> pending = new HashMap<>();
        run.getTaskRunList().forEach(taskRun -> pending.put(taskRun.getId(), taskRun.getId()));

        Execution current = run;
        for (int depth = 0; depth < MAX_REUSE_DEPTH && current.getParentId() != null && !pending.isEmpty(); depth++) {
            Optional<Execution> parent = executionRepository.findById(current.getTenantId(), current.getParentId())
                .filter(execution -> ExecutionKind.PLAYGROUND == execution.getKind());
            if (parent.isEmpty()) {
                break;
            }

            Map<String, TaskRun> parentByPosition = TaskRunPositions.byPosition(parent.get());
            Map<String, String> next = new HashMap<>();
            for (Map.Entry<String, TaskRun> entry : TaskRunPositions.byPosition(current).entrySet()) {
                String origin = pending.get(entry.getValue().getId());
                TaskRun source = parentByPosition.get(entry.getKey());
                if (origin != null && source != null && Objects.equals(source.getState(), entry.getValue().getState())) {
                    producers.put(origin, new ReusedTaskRun(parent.get().getId(), source.getId()));
                    next.put(source.getId(), origin);
                }
            }
            pending.clear();
            pending.putAll(next);
            current = parent.get();
        }
        return producers;
    }

    /** What the task of {@code taskRunId} sees when it renders an expression in {@code run}, secrets and KV values masked. */
    public Map<String, Object> context(Execution run, String taskRunId) throws InternalException {
        TaskRun taskRun = run.findTaskRunByTaskRunId(taskRunId);
        Flow flow = flowRepository.findByExecution(run);
        Map<String, Object> variables = runContextFactory
            .of(flow, flow.findTaskByTaskId(taskRun.getTaskId()), run, taskRun, false, secureVariableRendererFactory.createOrGet())
            .getVariables();

        Map<String, Object> context = new LinkedHashMap<>();
        variables.forEach((key, value) ->
        {
            if (CONTEXT_KEYS.contains(key)) {
                context.put(key, value);
            }
        });
        return context;
    }

    private void purge(Execution newRun, List<Execution> previousRuns) throws IOException {
        if (previousRuns.size() < RETAINED_RUNS) {
            return;
        }

        List<Execution> keptRuns = new ArrayList<>(previousRuns.subList(0, RETAINED_RUNS - 1));
        keptRuns.add(newRun);
        List<Execution> expiredRuns = previousRuns.subList(RETAINED_RUNS - 1, previousRuns.size())
            .stream()
            .filter(run -> run.getState().isTerminated())
            .toList();
        if (expiredRuns.isEmpty()) {
            return;
        }

        String keptReferences = references(keptRuns);
        for (Execution expired : expiredRuns) {
            // a reused task keeps pointing at the files of the run that first produced them
            executionService.delete(expired, true, true, !keptReferences.contains(expired.getId()));
        }
        taskOutputService.purge(expiredRuns);
        executionOutputService.purge(expiredRuns);
    }

    /** Everything a kept run can point into another run's storage with: its inputs and its task outputs, inline or stored. */
    private String references(List<Execution> keptRuns) throws IOException {
        StringBuilder references = new StringBuilder();
        for (Execution kept : keptRuns) {
            references.append(JacksonMapper.ofJson().writeValueAsString(kept.getInputs()));
            for (TaskOutput output : taskOutputRepository.findByExecution(kept)) {
                // ION stores strings as UTF-8, so a storage URI inside the binary value stays searchable as text
                if (output.value() != null) {
                    references.append(new String(output.value(), StandardCharsets.ISO_8859_1));
                }
                if (output.uri() != null) {
                    references.append(output.uri());
                }
            }
        }
        return references.toString();
    }

    /** The session's runs newest first; retention keeps it small enough to sort in memory. */
    private List<Execution> session(String tenantId, String namespace, String flowId, Optional<String> owner) {
        return executionRepository.find(Pageable.UNPAGED, tenantId, sessionFilters(namespace, flowId, owner))
            .stream()
            .sorted(Comparator.comparing(PlaygroundService::createdAt).reversed())
            .toList();
    }

    /** A replay copies its source's state history, so it was created at the last RESTARTED entry it appended. */
    static Instant createdAt(Execution run) {
        Instant started = run.getState().getStartDate();
        if (run.getParentId() == null) {
            return started;
        }
        return run.getState().getHistories().stream()
            .filter(history -> State.Type.RESTARTED == history.getState())
            .map(State.History::getDate)
            .max(Comparator.naturalOrder())
            .orElse(started);
    }

    private static List<QueryFilter> sessionFilters(String namespace, String flowId, Optional<String> owner) {
        List<QueryFilter> filters = new ArrayList<>(
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(namespace).build(),
                QueryFilter.builder().field(QueryFilter.Field.FLOW_ID).operation(QueryFilter.Op.EQUALS).value(flowId).build(),
                QueryFilter.builder().field(QueryFilter.Field.KIND).operation(QueryFilter.Op.EQUALS).value(ExecutionKind.PLAYGROUND).build()
            )
        );
        owner.ifPresent(
            username -> filters.add(
                QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of(Label.USERNAME, username)).build()
            )
        );
        return filters;
    }

    private static Optional<String> owner(Execution run) {
        return run.getLabels() == null ? Optional.empty()
            : run.getLabels().stream()
                .filter(label -> Label.USERNAME.equals(label.key()))
                .map(Label::value)
                .findFirst();
    }

    public record ReusedTaskRun(@NotNull String executionId, @NotNull String taskRunId) {
    }
}
