import {FilterConfiguration} from "../utils/types";
import {Comparators} from "../../../../composables/monaco/languages/filters/filterCompletion";
import {useValues} from "../../composables/useValues";

export const flowExecutionFilterConfiguration: FilterConfiguration = {
    title: "Flow Execution Filters",
    searchPlaceholder: "Search executions",
    keys: [
        {
            key: "state",
            label: "State",
            comparators: [Comparators.IN, Comparators.NOT_IN],
            valueType: "multi-select",
            valueProvider: async () => {
                const {VALUES} = useValues("executions");
                return VALUES.EXECUTION_STATES;
            }
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
            conflictsWith: ["scope"]
        },
        {
            key: "childFilter",
            label: "Child Filter",
            comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
            valueType: "select",
            valueProvider: async () => {
                const {VALUES} = useValues("executions");
                return VALUES.CHILDS;
            }
        },
        {
            key: "timeRange",
            label: "Interval",
            comparators: [Comparators.EQUALS],
            valueType: "select",
            valueProvider: async () => {
                const {VALUES} = useValues("executions");
                return VALUES.RELATIVE_DATE;
            }
        },
        {
            key: "labels",
            label: "Labels",
            comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS],
            valueType: "multi-select",
            valueProvider: async () => {
                // TODO: Implement API call to fetch distinct labels in key:value format
                return [];
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
            searchable: true
        }
    ]
};