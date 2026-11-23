import {useNamespacesStore} from "override/stores/namespaces"
import {useAuthStore} from "override/stores/auth"
import resource from "../../../models/resource"
import action from "../../../models/action"

/** Offers every namespace and each of its parents, or nothing to a user without the grant on any namespace. */
export function namespaceValueProvider(requiredResource: string = resource.NAMESPACE, requiredAction: string = action.LIST) {
    return async () => {
        if (!useAuthStore().user?.hasAnyActionOnAnyNamespace(requiredResource, requiredAction)) {
            return []
        }

        const namespaces = (await useNamespacesStore().loadAutocomplete()) as string[]
        const withParents = namespaces.flatMap(namespace =>
            namespace.split(".").map((_, index, parts) => parts.slice(0, index + 1).join(".")))

        return [...new Set(withParents)].map(namespace => ({label: namespace, value: namespace}))
    }
}
