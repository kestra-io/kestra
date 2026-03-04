import {computed, ComputedRef} from "vue";
import {FilterConfiguration, Comparators} from "../utils/filterTypes";
import {useValues} from "../composables/useValues";
import {useI18n} from "vue-i18n";

export const useFlowExecutionFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();

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
                        const {VALUES} = useValues("executions");
                        return VALUES.EXECUTION_STATES;
                    },
                    searchable: true,
                    visibleByDefault: true
                },
                {
                    key: "scope",
                    label: t("filter.scope.label"),
                    description: t("filter.scope.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "radio",
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions");
                        return VALUES.SCOPES;
                    },
                    showComparatorSelection: false
                },
                {
                    key: "childFilter",
                    label: t("filter.childFilter.label"),
                    description: t("filter.childFilter.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "radio",
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions");
                        return VALUES.CHILDS;
                    }
                },
                {
                    key: "kind",
                    label: t("filter.kind.label"),
                    description: t("filter.kind.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "radio",
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions");
                        return VALUES.KINDS;
                    }
                },
                {
                    key: "timeRange",
                    label: t("filter.timeRange.label"),
                    description: t("filter.timeRange.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("executions");
                        return VALUES.RELATIVE_DATE;
                    }
                },
                {
                    key: "labels",
                    label: t("filter.labels_execution.label"),
                    description: t("filter.labels_execution.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "key-value",
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
                        Comparators.ENDS_WITH
                    ],
                    valueType: "text",
                    searchable: true
                }
            ]
        };
    });
};