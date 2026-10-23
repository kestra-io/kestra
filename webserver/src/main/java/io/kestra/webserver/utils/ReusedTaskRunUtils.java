package io.kestra.webserver.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.utils.ListUtils;

import io.micronaut.core.annotation.Nullable;

/**
 * Reads the rows of an execution that reuses task runs from an earlier one (Playground, Replay): those rows were
 * recorded under the task run that actually ran, so they are fetched by task run rather than by execution.
 */
public final class ReusedTaskRunUtils {
    private ReusedTaskRunUtils() {
    }

    /** Where a task run's logs, outputs and metrics were recorded: the run it was reused from, or the run itself. */
    public record Source(String executionId, String taskRunId) {
    }

    /** The task runs this execution reused from an earlier one; empty when it reused none or is unknown. */
    public static List<TaskRun> reusedTaskRuns(@Nullable Execution execution) {
        return execution == null ? List.of() : execution.reusedTaskRuns();
    }

    /** Resolves a task run of the given execution to the task run that recorded its rows. */
    public static Source source(@Nullable Execution execution, String executionId, String taskRunId) {
        return execution == null ? new Source(executionId, taskRunId)
            : execution.reusedTaskRuns()
                .stream()
                .filter(taskRun -> taskRun.getId().equals(taskRunId))
                .findFirst()
                .map(taskRun -> new Source(taskRun.getSourceExecutionId(), taskRun.getSourceTaskRunId()))
                .orElseGet(() -> new Source(executionId, taskRunId));
    }

    /**
     * The user filters, with any task run they name resolved to the task run that really ran, plus the scope of the
     * execution itself. Filters are AND-ed by the store, so the URL path stays authoritative.
     */
    public static List<QueryFilter> executionFilters(String executionId, @Nullable Execution execution, @Nullable List<QueryFilter> userFilters) {
        List<TaskRun> reused = reusedTaskRuns(execution);

        List<QueryFilter> merged = new ArrayList<>(ListUtils.emptyOnNull(userFilters).size() + 1);
        ListUtils.emptyOnNull(userFilters).forEach(filter -> merged.add(resolveTaskRunIds(filter, reused)));
        merged.add(executionScope(executionId, reused));

        return merged;
    }

    /**
     * {@code executionId = <this execution>}, OR, per source execution, the task runs reused from it.
     */
    private static QueryFilter executionScope(String executionId, List<TaskRun> reused) {
        QueryFilter own = equalsFilter(QueryFilter.Field.EXECUTION_ID, executionId);
        if (reused.isEmpty()) {
            return own;
        }

        Map<String, List<String>> bySourceExecution = new LinkedHashMap<>();
        reused.forEach(taskRun -> bySourceExecution
            .computeIfAbsent(taskRun.getSourceExecutionId(), _ -> new ArrayList<>())
            .add(taskRun.getSourceTaskRunId())
        );

        List<QueryFilter> branches = new ArrayList<>(bySourceExecution.size() + 1);
        branches.add(own);
        bySourceExecution.forEach((sourceExecutionId, taskRunIds) -> branches.add(
            QueryFilter.builder()
                .logical(QueryFilter.Logical.AND)
                .children(List.of(
                    equalsFilter(QueryFilter.Field.EXECUTION_ID, sourceExecutionId),
                    taskRunIdFilter(QueryFilter.Op.IN, taskRunIds)
                ))
                .build()
        ));

        return QueryFilter.builder()
            .logical(QueryFilter.Logical.OR)
            .children(branches)
            .build();
    }

    /**
     * A reused task run has no row of its own, so a filter naming it is resolved to the task run that produced them.
     * Only EQUALS and IN are resolved, since a partial match (CONTAINS, STARTS_WITH) names no single task run.
     */
    private static QueryFilter resolveTaskRunIds(QueryFilter filter, List<TaskRun> reused) {
        if (reused.isEmpty()) {
            return filter;
        }

        if (filter.isNode()) {
            return QueryFilter.builder()
                .logical(filter.logical())
                .children(filter.children().stream().map(child -> resolveTaskRunIds(child, reused)).toList())
                .build();
        }

        if (filter.field() != QueryFilter.Field.TASK_RUN_ID) {
            return filter;
        }

        return switch (filter.operation()) {
            case EQUALS -> taskRunIdFilter(QueryFilter.Op.EQUALS, sourceTaskRunId(reused, filter.value()));
            case IN -> taskRunIdFilter(
                QueryFilter.Op.IN,
                ListUtils.convertToListString(filter.value()).stream().map(id -> sourceTaskRunId(reused, id)).toList()
            );
            default -> filter;
        };
    }

    private static Object sourceTaskRunId(List<TaskRun> reused, Object taskRunId) {
        return reused.stream()
            .filter(taskRun -> taskRun.getId().equals(String.valueOf(taskRunId)))
            .<Object> map(TaskRun::getSourceTaskRunId)
            .findFirst()
            .orElse(taskRunId);
    }

    private static QueryFilter taskRunIdFilter(QueryFilter.Op operation, Object value) {
        return QueryFilter.builder()
            .field(QueryFilter.Field.TASK_RUN_ID)
            .operation(operation)
            .value(value)
            .build();
    }

    private static QueryFilter equalsFilter(QueryFilter.Field field, String value) {
        return QueryFilter.builder()
            .field(field)
            .operation(QueryFilter.Op.EQUALS)
            .value(value)
            .build();
    }
}
