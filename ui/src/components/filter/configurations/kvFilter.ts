import {computed, ComputedRef} from "vue";
import {Comparators, FilterConfiguration} from "../utils/filterTypes";
import {useI18n} from "vue-i18n";
import {useNamespacesStore} from "override/stores/namespaces";
import {useAuthStore} from "override/stores/auth";
import {useRoute} from "vue-router";
import permission from "../../../models/permission";
import action from "../../../models/action";

export const useKvFilter = (): ComputedRef<FilterConfiguration> => {
    const {t} = useI18n();
    const route = useRoute();

    return computed(() => {
        return {
            title: t("filter.titles.kv_filters"),
            searchPlaceholder: t("filter.search_placeholders.search_kv"),
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
                    }
                ] : []) as any,
            ],
        };
    });
};
