import {computed, ComputedRef} from "vue";
import {FilterConfiguration, Comparators} from "../utils/filterTypes";
import permission from "../../../models/permission";
import action from "../../../models/action";
import {useNamespacesStore} from "override/stores/namespaces";
import {useAuthStore} from "override/stores/auth";
import {useValues} from "../composables/useValues";
import {useI18n} from "vue-i18n";
import {useRoute} from "vue-router";

export const useFlowFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();
    const route = useRoute();

    return computed(() => {
        return {
            title: t("filter.titles.flow_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_flows"),
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
                {
                    key: "scope",
                    label: t("filter.scope_flow.label"),
                    description: t("filter.scope_flow.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "radio",
                    valueProvider: async () => {
                        const {VALUES} = useValues("flows");
                        return VALUES.SCOPES;
                    },
                    showComparatorSelection: false
                },
                {
                    key: "labels",
                    label: t("filter.labels_flow.label"),
                    description: t("filter.labels_flow.description"),
                    comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
                    valueType: "key-value",
                },
            ]
        };
    });
}