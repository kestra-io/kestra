import {computed, ComputedRef} from "vue";
import {FilterConfiguration, Comparators} from "../utils/filterTypes";
import {useValues} from "../composables/useValues";
import {useI18n} from "vue-i18n";
import {useExecutionsStore} from "../../../stores/executions";

export const useGanttExecutionFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();

    return computed(() => {
        return {
            title: t("filter.titles.execution_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_executions"),
            keys: [
                {
                    key: "level",
                    label: t("filter.level_log_executions.label"),
                    description: t("filter.level.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("logs");
                        return VALUES.LEVELS;
                    },
                    defaultValue: () => (
                        typeof window !== "undefined"
                            ? localStorage.getItem("defaultLogLevel") || "INFO"
                            : "INFO"
                    ),
                    visibleByDefault: true
                },
                {
                    key: "state",
                    label: t("filter.state.label"),
                    description: t("filter.state.description"),
                    comparators: [Comparators.IN, Comparators.NOT_IN],
                    valueType: "multi-select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions");
                        return VALUES.EXECUTION_STATES;
                    },
                    showComparatorSelection: true,
                    searchable: true
                },
                {
                    key: "task",
                    label: t("filter.task.label"),
                    description: t("filter.task.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const taskRuns = useExecutionsStore().execution?.taskRunList ?? [];
                        return taskRuns.map((taskRun) => ({
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
