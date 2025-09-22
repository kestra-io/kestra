import {Comparators, Completion, FilterKeyCompletions} from "../filterCompletion";
import {FilterLanguage} from "../filterLanguage";
import permission from "../../../../../models/permission";
import action from "../../../../../models/action";
import {useAuthStore} from "override/stores/auth";
import {useNamespacesStore} from "override/stores/namespaces";

const flowFilterKeys: Record<string, FilterKeyCompletions> = {
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
    scope: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        async (hardcodedValues) => hardcodedValues.SCOPES,
        undefined,
        ["scope"]
    ),
    "labels.{key}": new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS],
        undefined,
        true
    ),
};

class FlowFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new FlowFilterLanguage();

    private constructor() {
        super("flow", flowFilterKeys);
    }
}

export default FlowFilterLanguage.INSTANCE as FilterLanguage;
