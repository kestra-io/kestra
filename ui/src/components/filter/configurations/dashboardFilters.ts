import {computed, ComputedRef} from "vue";
import {FilterConfiguration, Comparators} from "../utils/filterTypes";
import permission from "../../../models/permission";
import action from "../../../models/action";
import {useNamespacesStore} from "override/stores/namespaces";
import {useAuthStore} from "override/stores/auth";
import {useValues} from "../composables/useValues";
import {useI18n} from "vue-i18n";

export const useDashboardFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();

    return computed(() => {
        return {
            title: t("filter.titles.dashboard_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_dashboards"),
            keys: [
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
                    valueType: "multi-select",
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
                {
                    key: "timeRange",
                    label: t("filter.timeRange_dashboard.label"),
                    description: t("filter.timeRange_dashboard.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("dashboard");
                        return VALUES.RELATIVE_DATE;
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
                    searchable: true,
                    showComparatorSelection: true
                },
                {
                    key: "labels",
                    label: t("filter.labels.label"),
                    description: t("filter.labels.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "text",
                }
            ]
        };
    });
};

export const useNamespaceDashboardFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();

    return computed(() => {

        return {
            title: t("filter.titles.namespace_dashboard_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_dashboards"),
            keys: [
                {
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
                    // valueProvider: async () => {
                    //     const flowStore = useFlowStore();

                    //     const flowIds = await flowStore.loadDistinctFlowIds();
                    //     return flowIds.map((flowId: string) => ({label: flowId, value: flowId}));
                    // },
                    searchable: true
                },
                {
                    key: "timeRange",
                    label: t("filter.timeRange_dashboard.label"),
                    description: t("filter.timeRange_dashboard.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("dashboard");
                        return VALUES.RELATIVE_DATE;
                    }
                },
                {
                    key: "labels",
                    label: t("filter.labels.label"),
                    description: "Filter by labels",
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "text",
                }
            ]
        };
    });
};

export const useFlowDashboardFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();

    return computed(() => {

        return {
            title: t("filter.titles.flow_dashboard_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_dashboards"),
            keys: [
                {
                    key: "timeRange",
                    label: t("filter.timeRange_dashboard.label"),
                    description: t("filter.timeRange_dashboard.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("dashboard");
                        return VALUES.RELATIVE_DATE;
                    }
                },
                {
                    key: "labels",
                    label: t("filter.labels.label"),
                    description: t("filter.labels.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "text",
                }
            ]
        };
    });
};