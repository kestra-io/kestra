import {computed, ComputedRef} from "vue"
import {FilterConfiguration, Comparators} from "@kestra-io/design-system"
import {useValues} from "../composables/useValues"
import {useI18n} from "vue-i18n"
import {useRoute} from "vue-router"
import {routeFamily} from "../../../utils/routeFamily"
import {namespaceValueProvider} from "./namespaceValueProvider"

import {storageKeys} from "../../../utils/constants"
export const useLogFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()
    const route = useRoute()

    return computed(() => {
        return {
            title: t("filter.titles.log_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_logs"),
            keys: [
                ...(routeFamily(route.name) !== "namespaces/update" && routeFamily(route.name) !== "flows/update" ? [
                    {
                        key: "namespace",
                        label: t("filter.namespace.label"),
                        description: t("filter.namespace.description"),
                        comparators: [
                            Comparators.IN,
                            Comparators.NOT_IN,
                            Comparators.CONTAINS,
                            Comparators.PREFIX,
                        ],
                        valueType: "multi-select" as const,
                        valueProvider: namespaceValueProvider(),
                        searchable: true,
                    },
                ] : []) as FilterConfiguration["keys"],
                {
                    key: "level",
                    label: t("filter.level_log_executions.label"),
                    description: t("filter.level.description"),
                    comparators: [
                        Comparators.GREATER_THAN_OR_EQUAL_TO,
                        Comparators.LESS_THAN_OR_EQUAL_TO,
                        Comparators.IN,
                        Comparators.NOT_IN,
                    ],
                    comparatorLabels: {
                        [Comparators.GREATER_THAN_OR_EQUAL_TO]: "At or Above",
                        [Comparators.LESS_THAN_OR_EQUAL_TO]: "At or Below",
                    },
                    valueType: "multi-select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("logs")
                        return VALUES.LEVELS
                    },
                    defaultValue: () => (
                        typeof window !== "undefined"
                            ? localStorage.getItem(storageKeys.DEFAULT_LOG_LEVEL) || "INFO"
                            : "INFO"
                    ),
                    visibleByDefault: true,
                    colored: true,
                },
                {
                    key: "timeRange",
                    label: t("filter.timeRange_log.label"),
                    description: t("filter.timeRange_log.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    groupable: false,
                    valueProvider: async () => {
                        const {VALUES} = useValues("logs")
                        return VALUES.RELATIVE_DATE
                    },
                },
                {
                    key: "scope",
                    label: t("filter.scope_log.label"),
                    description: t("filter.scope_log.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "radio",
                    valueProvider: async () => {
                        const {VALUES} = useValues("logs")
                        return VALUES.SCOPES
                    },
                    showComparatorSelection: false,
                },
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
                },
                {
                    key: "triggerId",
                    label: t("filter.triggerId.label"),
                    description: t("filter.triggerId.description"),
                    comparators: [
                        // Comparators.IN,
                        // Comparators.NOT_IN,
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.CONTAINS,
                        Comparators.STARTS_WITH,
                        Comparators.ENDS_WITH,
                    ],
                    valueType: "text",
                },
                ...(routeFamily(route.name) !== "flows/update" ? [{
                    key: "flowId",
                    label: t("filter.flowId.label"),
                    description: t("filter.flowId.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.CONTAINS,
                        Comparators.STARTS_WITH,
                        Comparators.ENDS_WITH,
                    ],
                    valueType: "text",
                }] : []) as FilterConfiguration["keys"],
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
