import {computed, ComputedRef} from "vue"
import {FilterConfiguration, Comparators} from "@kestra-io/design-system"
import {useValues} from "../composables/useValues"
import {useI18n} from "vue-i18n"

export const useLogExecutionsFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()

    return computed(() => {
        return {
            title: t("filter.titles.log_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_logs"),
            keys: [
                {
                    key: "level",
                    label: t("filter.level_log_executions.label"),
                    description: t("filter.level.description"),
                    comparators: [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.LESS_THAN_OR_EQUAL_TO],
                    comparatorLabels: {
                        [Comparators.GREATER_THAN_OR_EQUAL_TO]: "At or Above",
                        [Comparators.LESS_THAN_OR_EQUAL_TO]: "At or Below",
                    },
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("logs")
                        return VALUES.LEVELS
                    },
                    defaultValue: () => (
                        typeof window !== "undefined"
                            ? localStorage.getItem("defaultLogLevel") || "INFO"
                            : "INFO"
                    ),
                    visibleByDefault: true,
                },
                {
                    key: "taskId",
                    label: t("filter.taskId.label"),
                    description: t("filter.taskId.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.CONTAINS,
                        Comparators.STARTS_WITH,
                        Comparators.ENDS_WITH,
                        Comparators.IN,
                    ],
                    valueType: "text",
                },
                {
                    key: "taskRunId",
                    label: t("filter.taskRunId.label"),
                    description: t("filter.taskRunId.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.IN,
                    ],
                    valueType: "text",
                },
                {
                    key: "attemptNumber",
                    label: t("filter.attemptNumber.label"),
                    description: t("filter.attemptNumber.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.IN,
                    ],
                    valueType: "text",
                },
            ],
        }
    })
}
