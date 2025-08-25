import {Comparators, Completion, FilterKeyCompletions, PICK_DATE_VALUE} from "../filterCompletion.ts";
import {FilterLanguage} from "../filterLanguage.ts";
import permission from "../../../../../models/permission.ts";
import action from "../../../../../models/action.ts";
import {useNamespacesStore} from "override/stores/namespaces.ts";
import {useAuthStore} from "override/stores/auth.ts";

const executionFilterKeys: Record<string, FilterKeyCompletions> = {
    namespace: new FilterKeyCompletions(
        [Comparators.PREFIX, Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.CONTAINS, Comparators.STARTS_WITH, Comparators.ENDS_WITH, Comparators.REGEX],
        async (_) => {
            const user = useAuthStore().user;
            if (user && user.hasAnyActionOnAnyNamespace(permission.NAMESPACE, action.READ)) {
                const namespacesStore = useNamespacesStore();
                return [...new Set(((await namespacesStore.loadAutocomplete()) as string[])
                    .flatMap(namespace => {
                        return namespace.split(".").reduce((current: string[], part: string) => {
                            const previousCombination = current?.[current.length - 1];
                            return [...current, `${(previousCombination ? previousCombination + "." : "")}${part}`];
                        }, [])
                    }))].map(namespace => new Completion(namespace, namespace));
            }

            return [];
        },
        true
    ),
    flowId: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.CONTAINS, Comparators.STARTS_WITH, Comparators.ENDS_WITH, Comparators.REGEX],
        undefined,
        true
    ),
    state: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        async (_, hardcodedValues) => hardcodedValues.EXECUTION_STATES,
        true
    ),
    scope: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        async (_, hardcodedValues) => hardcodedValues.SCOPES,
        undefined,
        ["scope"]
    ),
    childFilter: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        async (_, hardcodedValues) => hardcodedValues.CHILDS
    ),
    timeRange: new FilterKeyCompletions(
        [Comparators.EQUALS],
        async (_, hardcodedValues) => hardcodedValues.RELATIVE_DATE,
        false,
        ["timeRange", "startDate", "endDate"]
    ),
    startDate: new FilterKeyCompletions(
        [Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.GREATER_THAN, Comparators.LESS_THAN_OR_EQUAL_TO, Comparators.LESS_THAN, Comparators.EQUALS, Comparators.NOT_EQUALS],
        async () => PICK_DATE_VALUE,
        false,
        ["timeRange"]
    ),
    endDate: new FilterKeyCompletions(
        [Comparators.LESS_THAN_OR_EQUAL_TO, Comparators.LESS_THAN, Comparators.GREATER_THAN_OR_EQUAL_TO, Comparators.GREATER_THAN, Comparators.EQUALS, Comparators.NOT_EQUALS],
        async () => PICK_DATE_VALUE,
        false,
        ["timeRange"]
    ),
    "labels.{key}": new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        undefined,
        true
    ),
    triggerExecutionId: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.CONTAINS, Comparators.STARTS_WITH, Comparators.ENDS_WITH],
        undefined,
        true
    )
}

class ExecutionFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new ExecutionFilterLanguage();

    private constructor() {
        super("executions", executionFilterKeys);
    }
}

export default ExecutionFilterLanguage.INSTANCE as FilterLanguage;
