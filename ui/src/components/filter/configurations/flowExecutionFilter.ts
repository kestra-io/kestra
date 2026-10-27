import {computed, ComputedRef} from "vue"
import {FilterConfiguration, Comparators, FilterMeta} from "@kestra-io/design-system"
import {useMiscStore} from "override/stores/misc"
import {defaultTimeRange} from "../../executions/timeRangeWiden"
import {useValues} from "../composables/useValues"
import {useI18n} from "vue-i18n"
import {labelComparatorLabels} from "./labelComparatorLabels"

export const useFlowExecutionFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()
    const miscStore = useMiscStore()

    return computed(() => {
        return {
            title: t("filter.titles.flow_execution_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_executions"),
            keys: [
                {
                    key: "state",
                    label: t("filter.state.label"),
                    description: t("filter.state.description"),
                    comparators: [Comparators.IN, Comparators.NOT_IN],
                    valueType: "multi-select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions")
                        return VALUES.EXECUTION_STATES
                    },
                    visibleByDefault: true,
                    colored: true,
                },
                {
                    key: "scope",
                    label: t("filter.scope.label"),
                    description: t("filter.scope.description"),
                    comparators: [Comparators.IN, Comparators.NOT_IN],
                    valueType: "multi-select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions")
                        return VALUES.SCOPES
                    },
                    showComparatorSelection: false,
                },
                {
                    key: "childFilter",
                    label: t("filter.childFilter.label"),
                    description: t("filter.childFilter.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "radio",
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions")
                        return VALUES.CHILDS
                    },
                },
                {
                    key: "kind",
                    label: t("filter.kind.label"),
                    description: t("filter.kind.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "radio",
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions")
                        return VALUES.KINDS
                    },
                },
                {
                    key: "timeRange",
                    label: t("filter.timeRange.label"),
                    description: t("filter.timeRange.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    groupable: false,
                    removable: false,
                    clearedValue: defaultTimeRange(miscStore.configs?.chartDefaultDuration),
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions")
                        return VALUES.RELATIVE_DATE_OR_ALL_TIME
                    },
                    dateFilterOptions: [
                        {value: "START_DATE", label: t("filter.timeRange.dateFilter.startDate")},
                        {value: "END_DATE", label: t("filter.timeRange.dateFilter.endDate")},
                        {value: "START_OR_END_DATE", label: t("filter.timeRange.dateFilter.startOrEndDate")},
                    ],
                    keyLabelProvider: (meta?: FilterMeta) => {
                        switch (meta?.dateFilter) {
                        case "END_DATE": return t("filter.timeRange.chip.end")
                        case "START_OR_END_DATE": return t("filter.timeRange.chip.startOrEnd")
                        default: return t("filter.timeRange.chip.start")
                        }
                    },
                },
                {
                    key: "labels",
                    label: t("filter.labels_execution.label"),
                    description: t("filter.labels_execution.description"),
                    comparators: [
                        Comparators.IN,
                        Comparators.NOT_IN,
                        Comparators.EQUALS,
                        Comparators.CONTAINS,
                        Comparators.NOT_CONTAINS,
                        Comparators.IS_NOT_NULL,
                        Comparators.IS_NULL,
                    ],
                    comparatorLabels: labelComparatorLabels(t),
                    valueType: "key-value",
                    showComparatorSelection: true,
                },
                {
                    key: "outputs",
                    label: t("filter.outputs.label"),
                    description: t("filter.outputs.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.IN,
                        Comparators.NOT_IN,
                    ],
                    valueType: "key-value",
                    showComparatorSelection: true,
                },
                {
                    key: "triggerExecutionId",
                    label: t("filter.triggerExecutionId.label"),
                    description: t("filter.triggerExecutionId.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.CONTAINS,
                        Comparators.STARTS_WITH,
                        Comparators.ENDS_WITH,
                    ],
                    valueType: "text",
                },
                {
                    key: "triggerId",
                    label: t("filter.triggerId.label"),
                    description: t("filter.triggerId.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.CONTAINS,
                        Comparators.STARTS_WITH,
                        Comparators.ENDS_WITH,
                    ],
                    valueType: "text",
                },
                {
                    key: "parentId",
                    label: t("filter.parentId.label"),
                    description: t("filter.parentId.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                    ],
                    valueType: "text",
                },
                {
                    key: "taskId",
                    label: t("filter.taskId.label"),
                    description: t("filter.taskId.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                    ],
                    valueType: "text",
                },
            ],
        }
    })
}
