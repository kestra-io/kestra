import {FilterConfiguration} from "../utils/types";
import {Comparators} from "../../../../composables/monaco/languages/filters/filterCompletion";
import permission from "../../../../models/permission";
import action from "../../../../models/action";
import {useNamespacesStore} from "override/stores/namespaces";
import {useAuthStore} from "override/stores/auth";
import {useFlowStore} from "../../../../stores/flow";
import {useExecutionsStore} from "../../../../stores/executions";
import {useMiscStore} from "override/stores/misc";
import {useValues} from "../../composables/useValues";

export const executionFilterConfiguration: FilterConfiguration = {
    title: "Execution Filters",
    searchPlaceholder: "Search",
    keys: [
        {
            key: "namespace",
            label: "Namespace",
            comparators: [
                Comparators.IN,
                Comparators.NOT_IN,
                Comparators.PREFIX,
                Comparators.EQUALS,
                Comparators.NOT_EQUALS,
                Comparators.CONTAINS,
                Comparators.STARTS_WITH,
                Comparators.ENDS_WITH,
                Comparators.REGEX
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
            searchable: true,
        },
        {
            key: "flowId",
            label: "Flow ID",
            comparators: [
                Comparators.EQUALS,
                Comparators.NOT_EQUALS,
                Comparators.CONTAINS,
                Comparators.STARTS_WITH,
                Comparators.ENDS_WITH,
                Comparators.REGEX
            ],
            valueType: "multi-select",
            //TODO: Use API to fetch Flow IDs
            valueProvider: async () => {
                const flows = await useFlowStore().findFlows({size: 1000});
                const flowIds = [...new Set((flows.results || [])
                    .map((flow: any) => flow.id as string)
                    .filter(Boolean))] as string[];
                return flowIds.map(id => ({label: id, value: id}));
            },
            searchable: true
        },
        {
            key: "state",
            label: "State",
            comparators: [Comparators.IN, Comparators.NOT_IN],
            valueType: "multi-select",
            valueProvider: async () => {
                const {VALUES} = useValues("executions");
                return VALUES.EXECUTION_STATES;
            },
            showComparatorSelection: true
        },
        {
            key: "scope",
            label: "Scope",
            comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
            valueType: "multi-select",
            valueProvider: async () => {
                const {VALUES} = useValues("executions");
                return VALUES.SCOPES;
            },
            conflictsWith: ["scope"],
        },
        {
            key: "childFilter",
            label: "Child Filter",
            comparators: [Comparators.IN, Comparators.NOT_IN],
            valueType: "multi-select",
            valueProvider: async () => {
                const {VALUES} = useValues("executions");
                return VALUES.CHILDS;
            },
        },
        {
            key: "timeRange",
            label: "Interval",
            comparators: [Comparators.EQUALS],
            valueType: "select",
            valueProvider: async () => {
                const {VALUES} = useValues("executions");
                return VALUES.RELATIVE_DATE;
            },
        },
        {
            key: "labels",
            label: "Labels",
            comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
            valueType: "multi-select",
            // TODO: Implement API call to fetch distinct labels.
            valueProvider: async () => {
                const toIgnore = useMiscStore().configs?.hiddenLabelsPrefixes || [];
                const response = await useExecutionsStore().findExecutions({
                    commit: false,
                    page: 1,
                    size: 1000
                });
                
                const allLabels = new Set<string>();
                response.results?.forEach((execution: any) => {
                    execution.labels?.forEach((label: any) => {
                        if (!toIgnore.some((prefix: string) => label.key.startsWith(prefix))) {
                            allLabels.add(`${label.key}:${label.value}`);
                        }
                    });
                });

                return Array.from(allLabels).map(label => ({label, value: label}));
            },
            searchable: true,
            showComparatorSelection: true
        },
        {
            key: "triggerExecutionId",
            label: "Trigger Execution ID",
            comparators: [
                Comparators.EQUALS,
                Comparators.NOT_EQUALS,
                Comparators.CONTAINS,
                Comparators.STARTS_WITH,
                Comparators.ENDS_WITH
            ],
            valueType: "text",
        }
    ]
};