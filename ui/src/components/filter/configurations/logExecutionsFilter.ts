import {computed, ComputedRef, toValue, type MaybeRefOrGetter} from "vue"
import {FilterConfiguration, Comparators} from "@kestra-io/design-system"
import {useValues} from "../composables/useValues"
import {useI18n} from "vue-i18n"

export const useLogExecutionsFilter = (
    playground: MaybeRefOrGetter<boolean> = false,
    executionKind: MaybeRefOrGetter<string | undefined> = undefined,
): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()

    return computed(() => {
        const isPlayground = toValue(playground)
        // For non-NORMAL executions (PLAYGROUND, TEST, LOOP) the backend only returns that kind
        // when a kind filter is present, so surface it as a default-visible chip to make clear
        // which logs are shown.
        const kind = toValue(executionKind)
        const nonNormalKind = kind && kind !== "NORMAL" ? kind : undefined
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
                ...(isPlayground ? [] : [
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
                ]) as FilterConfiguration["keys"],
                ...(nonNormalKind ? [
                    {
                        key: "kind",
                        label: t("filter.kind.label"),
                        description: t("filter.kind.description"),
                        comparators: [Comparators.IN],
                        valueType: "multi-select",
                        valueProvider: async () => {
                            const {VALUES} = useValues("logs")
                            return VALUES.KINDS
                        },
                        defaultValue: () => [nonNormalKind],
                        visibleByDefault: true,
                    },
                ] : []) as FilterConfiguration["keys"],
            ],
        }
    })
}
