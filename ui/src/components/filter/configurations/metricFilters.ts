import {computed, ComputedRef} from "vue";
import {FilterConfiguration, Comparators} from "../utils/filterTypes";
import {useValues} from "../composables/useValues";
import {useFlowStore} from "../../../stores/flow";
import {useI18n} from "vue-i18n";
import {useExecutionsStore} from "../../../stores/executions";

export const useMetricFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();

    return computed(() => {
        return {
            title: t("filter.titles.metric_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_metrics"),
            keys: [
                {
                    key: "metric",
                    label: t("filter.metric.label"),
                    description: t("filter.metric.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const executionsStore = useExecutionsStore();
                        const taskRuns = executionsStore.execution?.taskRunList ?? [];
                        return taskRuns.map(taskRun => ({
                            label: taskRun.taskId + (taskRun.value ? ` - ${taskRun.value}` : ""),
                            value: taskRun.id
                        }));
                    },
                    searchable: true
                }
            ]
        };
    });
};

export const useFlowMetricFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();

    return computed(() => {
        return {
            title: t("filter.titles.flow_metric_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_metrics"),
            keys: [
                {
                    key: "task",
                    label: t("filter.task.label"),
                    description: t("filter.task.description"),
                    comparators: [
                        Comparators.EQUALS,
                    ],
                    valueType: "select",
                    valueProvider: async () => {
                        return (useFlowStore().tasksWithMetrics as string[]).map((value) => ({
                            label: value,
                            value
                        }));
                    },
                    searchable: true
                },
                {
                    key: "metric",
                    label: t("filter.metric.label"),
                    description: t("filter.metric.description"),
                    comparators: [
                        Comparators.EQUALS
                    ],
                    valueType: "select",
                    valueProvider: async () => {
                        return (useFlowStore().metrics as string[]).map((value) => ({
                            label: value,
                            value
                        }));
                    },
                    searchable: true
                },
                {
                    key: "aggregation",
                    label: t("filter.aggregation.label"),
                    description: t("filter.aggregation.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("metrics");
                        return [...VALUES.AGGREGATIONS, {label: "Count", value: "COUNT"}];
                    }
                },
                {
                    key: "timeRange",
                    label: t("filter.timeRange_metric.label"),
                    description: t("filter.timeRange_metric.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("metrics");
                        return VALUES.RELATIVE_DATE;
                    }
                }
            ]
        };
    });
};