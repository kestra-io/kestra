import {computed, ComputedRef} from "vue";
import {FilterConfiguration, Comparators} from "../utils/filterTypes";
import permission from "../../../models/permission";
import action from "../../../models/action";
import {useNamespacesStore} from "override/stores/namespaces";
import {useAuthStore} from "override/stores/auth";
import {useValues} from "../composables/useValues";
import {useI18n} from "vue-i18n";

export const useTriggerFilter = (): ComputedRef<FilterConfiguration> => computed(() => {
    const {t} = useI18n();

    return {
        title: t("filter.titles.trigger_filters"),
        searchPlaceholder: t("filter.search_placeholders.search_triggers"),
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
            },
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
                // valueProvider: async () => {},
                searchable: true,
            }
        ]
    };
});