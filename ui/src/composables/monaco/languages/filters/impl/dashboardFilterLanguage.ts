import {Comparators, Completion, FilterKeyCompletions, PICK_DATE_VALUE} from "../filterCompletion";
import {FilterLanguage} from "../filterLanguage";
import permission from "../../../../../models/permission";
import action from "../../../../../models/action";
import {useAuthStore} from "override/stores/auth";
import {useNamespacesStore} from "override/stores/namespaces";

const dashboardFilterKeys: Record<string, FilterKeyCompletions> = {
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
    timeRange: new FilterKeyCompletions(
        [Comparators.EQUALS],
        async (hardcodedValues) => hardcodedValues.RELATIVE_DATE,
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
}

class DashboardFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new DashboardFilterLanguage();

    private constructor() {
        super("dashboards", dashboardFilterKeys, false);
    }
}

export default DashboardFilterLanguage.INSTANCE as FilterLanguage;
