package io.kestra.core.models.collectors;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.FormInput;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@SuperBuilder(toBuilder = true)
@Getter
@Jacksonized
public class FlowUsage {

    // Namespace used for 'Getting Started' flows.
    private static final String TUTORIAL_NAMESPACE = "tutorial";

    private final Long count;
    private final Long namespacesCount;
    private final Map<String, Long> taskTypeCount;
    private final Map<String, Long> triggerTypeCount;
    private final Map<String, Long> taskRunnerTypeCount;
    private final Map<String, Long> inputTypeCount;
    private final Long hasInputsCount;
    private final Long hasOutputsCount;
    private final Long hasLabelsCount;
    private final Long hasVariablesCount;
    private final Long hasWorkerSelectorCount;
    private final Long hasErrorsCount;
    private final Long hasFinallyCount;
    private final Long hasAfterExecutionCount;
    private final Long hasTriggersCount;
    private final Long hasConcurrencyCount;
    private final Long hasRetryCount;
    private final Long hasSlaCount;
    private final Long hasChecksCount;
    private final Long hasQuotasCount;
    private final TaskUsage tasks;
    private final TriggerUsage triggers;

    public static FlowUsage of(String tenantId, FlowRepositoryInterface flowRepository) {
        return FlowUsage.of(flowRepository.findAll(tenantId));
    }

    public static FlowUsage of(FlowRepositoryInterface flowRepository) {
        return FlowUsage.of(flowRepository.findAllForAllTenants());
    }

    public static FlowUsage of(List<Flow> flows) {
        List<Flow> filtered = flows.stream()
            .filter(flow -> !TUTORIAL_NAMESPACE.equals(flow.getNamespace()))
            .toList();
        List<Task> allTasks = filtered.stream()
            .flatMap(flow -> flow.allTasks())
            .toList();
        List<AbstractTrigger> allTriggers = filtered.stream()
            .flatMap(flow -> ListUtils.emptyOnNull(flow.getTriggers()).stream())
            .toList();
        List<Input<?>> allInputs = filtered.stream()
            .flatMap(flow -> ListUtils.emptyOnNull(flow.getInputs()).stream())
            .toList();

        LongAdder count = new LongAdder();
        LongAdder hasInputsCount = new LongAdder();
        LongAdder hasOutputsCount = new LongAdder();
        LongAdder hasLabelsCount = new LongAdder();
        LongAdder hasVariablesCount = new LongAdder();
        LongAdder hasWorkerSelectorCount = new LongAdder();
        LongAdder hasErrorsCount = new LongAdder();
        LongAdder hasFinallyCount = new LongAdder();
        LongAdder hasAfterExecutionCount = new LongAdder();
        LongAdder hasTriggersCount = new LongAdder();
        LongAdder hasConcurrencyCount = new LongAdder();
        LongAdder hasRetryCount = new LongAdder();
        LongAdder hasSlaCount = new LongAdder();
        LongAdder hasChecksCount = new LongAdder();
        LongAdder hasQuotasCount = new LongAdder();
        filtered.forEach(flow ->
        {
            count.increment();
            if (!ListUtils.isEmpty(flow.getInputs())) {
                hasInputsCount.increment();
            }
            if (!ListUtils.isEmpty(flow.getOutputs())) {
                hasOutputsCount.increment();
            }
            if (!ListUtils.isEmpty(flow.getLabels())) {
                hasLabelsCount.increment();
            }
            if (!MapUtils.isEmpty(flow.getVariables())) {
                hasVariablesCount.increment();
            }
            if (flow.getWorkerSelector() != null) {
                hasWorkerSelectorCount.increment();
            }
            if (!ListUtils.isEmpty(flow.getErrors())) {
                hasErrorsCount.increment();
            }
            if (!ListUtils.isEmpty(flow.getFinally())) {
                hasFinallyCount.increment();
            }
            if (!ListUtils.isEmpty(flow.getAfterExecution())) {
                hasAfterExecutionCount.increment();
            }
            if (!ListUtils.isEmpty(flow.getTriggers())) {
                hasTriggersCount.increment();
            }
            if (flow.getConcurrency() != null) {
                hasConcurrencyCount.increment();
            }
            if (flow.getRetry() != null) {
                hasRetryCount.increment();
            }
            if (!ListUtils.isEmpty(flow.getSla())) {
                hasSlaCount.increment();
            }
            if (!ListUtils.isEmpty(flow.getChecks())) {
                hasChecksCount.increment();
            }
            if (!ListUtils.isEmpty(flow.getQuotas())) {
                hasQuotasCount.increment();
            }
        });

        return FlowUsage.builder()
            .count(count.longValue())
            .namespacesCount(namespacesCount(filtered))
            .taskTypeCount(taskTypeCount(allTasks))
            .triggerTypeCount(triggerTypeCount(allTriggers))
            .taskRunnerTypeCount(taskRunnerTypeCount(allTasks))
            .inputTypeCount(inputTypeCount(allInputs))
            .hasInputsCount(hasInputsCount.longValue())
            .hasOutputsCount(hasOutputsCount.longValue())
            .hasLabelsCount(hasLabelsCount.longValue())
            .hasVariablesCount(hasVariablesCount.longValue())
            .hasWorkerSelectorCount(hasWorkerSelectorCount.longValue())
            .hasErrorsCount(hasErrorsCount.longValue())
            .hasFinallyCount(hasFinallyCount.longValue())
            .hasAfterExecutionCount(hasAfterExecutionCount.longValue())
            .hasTriggersCount(hasTriggersCount.longValue())
            .hasConcurrencyCount(hasConcurrencyCount.longValue())
            .hasRetryCount(hasRetryCount.longValue())
            .hasSlaCount(hasSlaCount.longValue())
            .hasChecksCount(hasChecksCount.longValue())
            .hasQuotasCount(hasQuotasCount.longValue())
            .tasks(TaskUsage.of(allTasks))
            .triggers(TriggerUsage.of(allTriggers))
            .build();
    }

    private static long namespacesCount(List<Flow> allFlows) {
        return allFlows
            .stream()
            .map(Flow::getNamespace)
            .distinct()
            .count();
    }

    private static Map<String, Long> taskTypeCount(List<Task> allTasks) {
        return allTasks
            .stream()
            .collect(Collectors.groupingBy(f -> f.getType(), Collectors.counting()));
    }

    private static Map<String, Long> triggerTypeCount(List<AbstractTrigger> allTriggers) {
        return allTriggers
            .stream()
            .collect(Collectors.groupingBy(f -> f.getType(), Collectors.counting()));
    }

    /**
     * Groups {@code inputs} by {@link Input#getType()}, recursing into {@link FormInput#getInputs()}.
     * Public so EE can reuse it for reusable-inputs block definitions (see {@code FeatureUsageReport}).
     */
    public static Map<String, Long> inputTypeCount(List<Input<?>> inputs) {
        if (ListUtils.isEmpty(inputs)) {
            return Map.of();
        }

        return inputs.stream()
            .flatMap(
                input -> input instanceof FormInput form
                    ? Stream.concat(Stream.of(input.getType().name()), ListUtils.emptyOnNull(form.getInputs()).stream().map(i -> i.getType().name()))
                    : Stream.of(input.getType().name())
            )
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
    }

    private static Map<String, Long> taskRunnerTypeCount(List<Task> allTask) {
        return allTask
            .stream()
            .filter(t ->
            {
                try {
                    return t.getClass().getMethod("getTaskRunner") != null;
                } catch (NoSuchMethodException e) {
                    return false;
                }
            })
            .map(t ->
            {
                try {
                    TaskRunner<?> taskRunner = (TaskRunner<?>) t.getClass().getMethod("getTaskRunner").invoke(t);
                    return taskRunner != null ? taskRunner.getType() : null;
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(f -> f, Collectors.counting()));
    }

    @SuperBuilder
    @Getter
    @Jacksonized
    public static class TaskUsage {
        private final Long hasRetryCount;
        private final Long hasTimeoutCount;
        private final Long hasWorkerSelectorCount;
        private final Long hasAllowFailureCount;
        private final Long hasLogToFileCount;
        private final Long hasRunIfCount;
        private final Long hasAllowWarningCount;
        private final Long hasCacheCount;
        private final Long hasAssetsCount;
        private final Long hasErrorsCount;
        private final Long hasFinallyCount;

        static TaskUsage of(List<Task> allTasks) {
            LongAdder hasRetryCount = new LongAdder();
            LongAdder hasTimeoutCount = new LongAdder();
            LongAdder hasWorkerSelectorCount = new LongAdder();
            LongAdder hasAllowFailureCount = new LongAdder();
            LongAdder hasLogToFileCount = new LongAdder();
            LongAdder hasRunIfCount = new LongAdder();
            LongAdder hasAllowWarningCount = new LongAdder();
            LongAdder hasCacheCount = new LongAdder();
            LongAdder hasAssetsCount = new LongAdder();
            LongAdder hasErrorsCount = new LongAdder();
            LongAdder hasFinallyCount = new LongAdder();
            allTasks.forEach(task ->
            {
                if (task.getRetry() != null) {
                    hasRetryCount.increment();
                }
                if (task.getTimeout() != null) {
                    hasTimeoutCount.increment();
                }
                if (task.getWorkerSelector() != null) {
                    hasWorkerSelectorCount.increment();
                }
                if (task.isAllowFailure()) {
                    hasAllowFailureCount.increment();
                }
                if (task.isLogToFile()) {
                    hasLogToFileCount.increment();
                }
                if (task.getRunIf() != null && !"true".equals(task.getRunIf())) {
                    hasRunIfCount.increment();
                }
                if (task.isAllowWarning()) {
                    hasAllowWarningCount.increment();
                }
                if (task.getTaskCache() != null) {
                    hasCacheCount.increment();
                }
                if (task.getAssets() != null) {
                    hasAssetsCount.increment();
                }
                if (task instanceof FlowableTask<?> flowableTask) {
                    if (!ListUtils.isEmpty(flowableTask.getErrors())) {
                        hasErrorsCount.increment();
                    }
                    if (!ListUtils.isEmpty(flowableTask.getFinally())) {
                        hasFinallyCount.increment();
                    }
                }
            });

            return TaskUsage.builder()
                .hasRetryCount(hasRetryCount.longValue())
                .hasTimeoutCount(hasTimeoutCount.longValue())
                .hasWorkerSelectorCount(hasWorkerSelectorCount.longValue())
                .hasAllowFailureCount(hasAllowFailureCount.longValue())
                .hasLogToFileCount(hasLogToFileCount.longValue())
                .hasRunIfCount(hasRunIfCount.longValue())
                .hasAllowWarningCount(hasAllowWarningCount.longValue())
                .hasCacheCount(hasCacheCount.longValue())
                .hasAssetsCount(hasAssetsCount.longValue())
                .hasErrorsCount(hasErrorsCount.longValue())
                .hasFinallyCount(hasFinallyCount.longValue())
                .build();
        }
    }

    @SuperBuilder
    @Getter
    @Jacksonized
    public static class TriggerUsage {
        private final Long hasWhenCount;
        private final Long hasWorkerSelectorCount;
        private final Long hasLabelsCount;
        private final Long hasStopAfterCount;
        private final Long hasLogToFileCount;
        private final Long hasFailOnErrorCount;
        private final Long hasAllowConcurrentCount;
        private final Long hasAssetsCount;

        static TriggerUsage of(List<AbstractTrigger> allTriggers) {
            LongAdder hasWhenCount = new LongAdder();
            LongAdder hasWorkerSelectorCount = new LongAdder();
            LongAdder hasLabelsCount = new LongAdder();
            LongAdder hasStopAfterCount = new LongAdder();
            LongAdder hasLogToFileCount = new LongAdder();
            LongAdder hasFailOnErrorCount = new LongAdder();
            LongAdder hasAllowConcurrentCount = new LongAdder();
            LongAdder hasAssetsCount = new LongAdder();
            allTriggers.forEach(trigger ->
            {
                hasWhenCount.add(trigger.getWhen() != null ? 1 : 0);
                hasWorkerSelectorCount.add(trigger.getWorkerSelector() != null ? 1 : 0);
                hasLabelsCount.add(!ListUtils.isEmpty(trigger.getLabels()) ? 1 : 0);
                hasStopAfterCount.add(trigger.getStopAfter() != null ? 1 : 0);
                hasLogToFileCount.add(trigger.isLogToFile() ? 1 : 0);
                hasFailOnErrorCount.add(trigger.isFailOnTriggerError() ? 1 : 0);
                hasAllowConcurrentCount.add(trigger.isAllowConcurrent() ? 1 : 0);
                hasAssetsCount.add(trigger.getAssets() != null ? 1 : 0);
            });

            return TriggerUsage.builder()
                .hasWhenCount(hasWhenCount.longValue())
                .hasWorkerSelectorCount(hasWorkerSelectorCount.longValue())
                .hasLabelsCount(hasLabelsCount.longValue())
                .hasStopAfterCount(hasStopAfterCount.longValue())
                .hasLogToFileCount(hasLogToFileCount.longValue())
                .hasFailOnErrorCount(hasFailOnErrorCount.longValue())
                .hasAllowConcurrentCount(hasAllowConcurrentCount.longValue())
                .hasAssetsCount(hasAssetsCount.longValue())
                .build();
        }
    }
}
