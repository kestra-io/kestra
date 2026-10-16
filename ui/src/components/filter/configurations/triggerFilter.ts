import {computed, ComputedRef} from "vue"
import {FilterConfiguration, Comparators, FilterMeta} from "@kestra-io/design-system"
import {useValues} from "../composables/useValues"
import {useI18n} from "vue-i18n"
import {useRoute} from "vue-router"
import {routeFamily} from "../../../utils/routeFamily"
import {namespaceValueProvider} from "./namespaceValueProvider"

export const useTriggerFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n()
    const route = useRoute()

    return computed(() => {
        // `t` is handed over so this never re-enters useI18n: the computed also refreshes outside a
        // setup context (a flush job after a route-query change), where useI18n throws.
        const {VALUES} = useValues("triggers", t)

        return {
            title: t("filter.titles.trigger_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_triggers"),
            keys: [
                ...(routeFamily(route.name) !== "namespaces/update" ? [
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
                    key: "timeRange",
                    label: t("filter.timeRange_trigger.label"),
                    description: t("filter.timeRange_trigger.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    groupable: false,
                    valueProvider: async (options?: {meta?: FilterMeta}) => {
                        return options?.meta?.dateFilter === "LAST_TRIGGERED_DATE"
                            ? VALUES.RELATIVE_DATE
                            : VALUES.RELATIVE_DATE_NEXT
                    },
                    dateFilterOptions: [
                        {value: "NEXT_EXECUTION_DATE", label: t("filter.timeRange_trigger.dateFilter.nextExecutionDate")},
                        {value: "LAST_TRIGGERED_DATE", label: t("filter.timeRange_trigger.dateFilter.lastTriggeredDate")},
                    ],
                    keyLabelProvider: (meta?: FilterMeta) => {
                        return meta?.dateFilter === "LAST_TRIGGERED_DATE"
                            ? t("filter.timeRange_trigger.chip.lastTriggered")
                            : t("filter.timeRange_trigger.chip.nextExecution")
                    },
                },
                {
                    key: "scope",
                    label: t("filter.scope_trigger.label"),
                    description: t("filter.scope_trigger.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "radio",
                    valueProvider: async () => VALUES.SCOPES,
                    showComparatorSelection: false,
                },
                {
                    key: "triggerId",
                    label: t("filter.triggerId_trigger.label"),
                    description: t("filter.triggerId_trigger.description"),
                    comparators: [
                        Comparators.IN,
                        Comparators.NOT_IN,
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.CONTAINS,
                        Comparators.STARTS_WITH,
                        Comparators.ENDS_WITH,
                    ],
                    valueType: "text",
                },
                {
                    key: "workerId",
                    label: t("filter.workerId.label"),
                    description: t("filter.workerId.description"),
                    comparators: [
                        Comparators.IN,
                        Comparators.NOT_IN,
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                        Comparators.CONTAINS,
                        Comparators.STARTS_WITH,
                        Comparators.ENDS_WITH,
                    ],
                    valueType: "text",
                    searchable: true,
                },
                {
                    key: "triggerState",
                    label: t("filter.triggerState.label"),
                    description: t("filter.triggerState.description"),
                    comparators: [
                        Comparators.EQUALS,
                        Comparators.NOT_EQUALS,
                    ],
                    valueType: "select",
                    valueProvider: async () => VALUES.TRIGGER_STATES,
                },
                {
                    // QueryFilter.Field.LOCKED supports EQUALS only, so there is no comparator to offer.
                    key: "locked",
                    label: t("filter.triggerLocked.label"),
                    description: t("filter.triggerLocked.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => VALUES.TRIGGER_LOCK_STATES,
                },
                {
                    // Keyed `source` after QueryFilter.Field.SOURCE, but labelled "Kind": it targets the
                    // scheduler's trigger type, which the API exposes as `state.kind` so it does not clash
                    // with the trigger definition's plugin type.
                    key: "source",
                    label: t("filter.triggerKind.label"),
                    description: t("filter.triggerKind.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => VALUES.TRIGGER_KINDS,
                },
                {
                    // QueryFilter.Field.OPERATION_ID supports EQUALS only, so there is no comparator to offer.
                    key: "operationId",
                    label: t("filter.operationId.label"),
                    description: t("filter.operationId.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "text",
                },
            ],
        }
    })
}
