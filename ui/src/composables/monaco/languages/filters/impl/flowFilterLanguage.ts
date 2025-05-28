import {Comparators, Completion, FilterKeyCompletions} from "../filterCompletion.ts";
import {FilterLanguage} from "../filterLanguage.ts";
import {Me} from "../../../../../stores/auth.ts";
import permission from "../../../../../models/permission.ts";
import action from "../../../../../models/action.ts";

const flowFilterKeys: Record<string, FilterKeyCompletions> = {
    namespace: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.CONTAINS, Comparators.STARTS_WITH, Comparators.ENDS_WITH, Comparators.REGEX],
        async (store) => {
            const user = store.getters["auth/user"] as Me;
            if (user && user.hasAnyActionOnAnyNamespace(permission.NAMESPACE, action.READ)) {
                return [...new Set(((await store.dispatch("namespace/loadNamespacesForDatatype", {dataType: "flow"})) as string[])
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
        async (_, hardcodedValues) => hardcodedValues.SCOPES
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