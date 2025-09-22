import {Comparators, Completion, FilterKeyCompletions, PICK_DATE_VALUE} from "../filterCompletion";
import {FilterLanguage} from "../filterLanguage";
import permission from "../../../../../models/permission";
import action from "../../../../../models/action";
import {useAuthStore} from "override/stores/auth";
import {useFlowStore} from "../../../../../stores/flow";
import {useNamespacesStore} from "override/stores/namespaces";

const namespaceDashboardFilterKeys: Record<string, FilterKeyCompletions> = {
    flowId: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.CONTAINS, Comparators.STARTS_WITH, Comparators.ENDS_WITH, Comparators.REGEX],
        async (_) => {
            const namespacesStore = useNamespacesStore();
            const namespaceId = namespacesStore.namespace?.id
            const user = useAuthStore().user;
            const flowStore = useFlowStore();
            if (namespaceId !== undefined && user && user.hasAnyActionOnAnyNamespace(permission.FLOW, action.READ)) {
                return ((await flowStore.flowsByNamespace(namespaceId)) as { id: string }[])
                    .map(({id}) => new Completion(id, id));
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

class NamespaceDashboardFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new NamespaceDashboardFilterLanguage();

    private constructor() {
        super("namespace-dashboards", namespaceDashboardFilterKeys, false);
    }
}

export default NamespaceDashboardFilterLanguage.INSTANCE as FilterLanguage;
