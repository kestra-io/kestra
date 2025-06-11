import {Comparators, Completion, FilterKeyCompletions, PICK_DATE_VALUE} from "../filterCompletion.ts";
import {FilterLanguage} from "../filterLanguage.ts";
import permission from "../../../../../models/permission.ts";
import action from "../../../../../models/action.ts";
import {Me} from "../../../../../stores/auth.ts";

const namespaceDashboardFilterKeys: Record<string, FilterKeyCompletions> = {
    flowId: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.CONTAINS, Comparators.STARTS_WITH, Comparators.ENDS_WITH, Comparators.REGEX],
        async (store) => {
            const namespaceId = store.state.namespace.namespace?.id
            const user = store.getters["auth/user"] as Me;
            if (namespaceId !== undefined && user && user.hasAnyActionOnAnyNamespace(permission.FLOW, action.READ)) {
                return ((await store.dispatch("flow/flowsByNamespace", namespaceId)) as { id: string }[])
                    .map(({id}) => new Completion(id, id));
            }

            return [];
        },
        true
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
}

class NamespaceDashboardFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new NamespaceDashboardFilterLanguage();

    private constructor() {
        super("namespace-dashboards", namespaceDashboardFilterKeys, false);
    }
}

export default NamespaceDashboardFilterLanguage.INSTANCE as FilterLanguage;
