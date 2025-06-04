import {Comparators, Completion, FilterKeyCompletions} from "../filterCompletion.ts";
import {FilterLanguage} from "../filterLanguage.ts";
import permission from "../../../../../models/permission.ts";
import action from "../../../../../models/action.ts";
import {Me} from "../../../../../stores/auth.ts";

const triggerFilterKeys: Record<string, FilterKeyCompletions> = {
    namespace: new FilterKeyCompletions(
        [Comparators.PREFIX, Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.CONTAINS, Comparators.STARTS_WITH, Comparators.ENDS_WITH, Comparators.REGEX],
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
    flowId: new FilterKeyCompletions(
        [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.CONTAINS, Comparators.STARTS_WITH, Comparators.ENDS_WITH, Comparators.REGEX],
        undefined,
        true
    ),
}

class TriggerFilterLanguage extends FilterLanguage {
    static readonly INSTANCE = new TriggerFilterLanguage();

    private constructor() {
        super("triggers", triggerFilterKeys);
    }
}

export default TriggerFilterLanguage.INSTANCE as FilterLanguage;