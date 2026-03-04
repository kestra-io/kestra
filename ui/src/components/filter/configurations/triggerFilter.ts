import {computed, ComputedRef} from "vue";
import {FilterConfiguration, Comparators} from "../utils/filterTypes";
import permission from "../../../models/permission";
import action from "../../../models/action";
import {useNamespacesStore} from "override/stores/namespaces";
import {useAuthStore} from "override/stores/auth";
import {useValues} from "../composables/useValues";
import {useI18n} from "vue-i18n";
import {useRoute} from "vue-router";

export const useTriggerFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();
    const route = useRoute();

    return computed(() => {
        return {
            title: t("filter.titles.trigger_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_triggers"),
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
                    key: "timeRange",
                    label: t("filter.timeRange_trigger.label"),
                    description: t("filter.timeRange_trigger.description"),
                    comparators: [Comparators.EQUALS],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("triggers");
                        return VALUES.RELATIVE_DATE;
                    }
                },
                {
                    key: "scope",
                    label: t("filter.scope_trigger.label"),
                    description: t("filter.scope_trigger.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "radio",
                    valueProvider: async () => {
                        const {VALUES} = useValues("triggers");
                        return VALUES.SCOPES;
                    },
                    showComparatorSelection: false
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
                        Comparators.ENDS_WITH
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
                        Comparators.ENDS_WITH
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
                        Comparators.NOT_EQUALS
                    ],
                    valueType: "select",
                    valueProvider: async () => {
                        const {VALUES} = useValues("triggers");
                        return VALUES.TRIGGER_STATES;
                    }
                }
            ]
        };
    });
};
