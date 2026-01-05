import {computed, ComputedRef} from "vue";
import {FilterConfiguration, Comparators} from "../utils/filterTypes";
import permission from "../../../models/permission";
import action from "../../../models/action";
import {useNamespacesStore} from "override/stores/namespaces";
import {useAuthStore} from "override/stores/auth";
import {useValues} from "../composables/useValues";
import {useI18n} from "vue-i18n";
import {useRoute} from "vue-router";

export const useExecutionFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();
    const route = useRoute();

    return computed(() => {
        return {
            title: t("filter.titles.execution_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_executions"),
            keys: [
                ...(route.name !== "namespaces/update" ? [
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
                        valueProvider: async () => {
                            const user = useAuthStore().user;
                            if (user && user.hasAnyActionOnAnyNamespace(permission.NAMESPACE, action.READ)) {
                                const namespacesStore = useNamespacesStore();
                                const namespaces = (await namespacesStore.loadAutocomplete()) as string[];
                                return [...new Set(namespaces
                                    .flatMap(namespace => {
                                        return namespace.split(".").reduce((current: string[], part: string) => {
                                            const previousCombination = current?.[current.length - 1];
                                            return [...current, `${(previousCombination ? previousCombination + "." : "")}${part}`];
                                        }, []);
                                    }))].map(namespace => ({
                                        label: namespace,
                                        value: namespace
                                    }));
                            }
                            return [];
                        },
                        searchable: true
                    },
                ] : []) as any,
                ...(route.name !== "flows/update" ? [{
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
                }] : []) as any,
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