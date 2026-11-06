package io.kestra.webserver.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ExecutionOutputService;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskOutputService;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the {@value #RETAINED_RUNS} most recent Playground runs per user and flow and deletes the older ones.
 */
@Slf4j
@Singleton
public class PlaygroundRetentionService {
    public static final int RETAINED_RUNS = 10;

    private final ExecutionRepositoryInterface executionRepository;
    private final TaskOutputRepositoryInterface taskOutputRepository;
    private final ExecutionService executionService;
    private final TaskOutputService taskOutputService;
    private final ExecutionOutputService executionOutputService;

    @Inject
    public PlaygroundRetentionService(
        ExecutionRepositoryInterface executionRepository,
        TaskOutputRepositoryInterface taskOutputRepository,
        ExecutionService executionService,
        TaskOutputService taskOutputService,
        ExecutionOutputService executionOutputService) {
        this.executionRepository = Objects.requireNonNull(executionRepository);
        this.taskOutputRepository = Objects.requireNonNull(taskOutputRepository);
        this.executionService = Objects.requireNonNull(executionService);
        this.taskOutputService = Objects.requireNonNull(taskOutputService);
        this.executionOutputService = Objects.requireNonNull(executionOutputService);
    }

    /**
     * Deletes the terminated Playground runs older than the newest ones of {@code newRun}'s user and flow, {@code newRun} included.
     * A no-op for any other kind; failures are logged, never thrown, so a purge cannot fail the run that triggered it.
     */
    public void purgeOlderRuns(Execution newRun) {
        if (ExecutionKind.PLAYGROUND != newRun.getKind()) {
            return;
        }

        try {
            purge(newRun);
        } catch (Exception e) {
            log.warn("Unable to purge the older Playground runs of flow '{}.{}'.", newRun.getNamespace(), newRun.getFlowId(), e);
        }
    }

    private void purge(Execution newRun) throws IOException {
        List<Execution> previousRuns = executionRepository
            .find(newestFirst(), newRun.getTenantId(), sameUserAndFlow(newRun))
            .stream()
            .filter(run -> !run.getId().equals(newRun.getId()))
            .toList();
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
            // a replayed task keeps pointing at the files of the run that first produced them
            executionService.delete(expired, true, true, !keptReferences.contains(expired.getId()));
        }
        taskOutputService.purge(expiredRuns);
        executionOutputService.purge(expiredRuns);
    }

    private Pageable newestFirst() {
        return Pageable.UNPAGED.withSort(Sort.of(Sort.Order.desc(executionRepository.sortMapping().apply(Execution.STATE_START_DATE_FIELD))));
    }

    private static List<QueryFilter> sameUserAndFlow(Execution run) {
        List<QueryFilter> filters = new ArrayList<>(List.of(
            QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value(run.getNamespace()).build(),
            QueryFilter.builder().field(QueryFilter.Field.FLOW_ID).operation(QueryFilter.Op.EQUALS).value(run.getFlowId()).build(),
            QueryFilter.builder().field(QueryFilter.Field.KIND).operation(QueryFilter.Op.EQUALS).value(ExecutionKind.PLAYGROUND).build()
        ));
        run.getLabels().stream()
            .filter(label -> Label.USERNAME.equals(label.key()))
            .findFirst()
            .ifPresent(label -> filters.add(
                QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of(label.key(), label.value())).build()
            ));
        return filters;
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
}
