import {EntityIterator} from "./entityIterator"
import {useNamespacesStore} from "override/stores/namespaces"
import {storageKeys} from "../utils/constants"
import {Namespace} from "@kestra-io/kestra-sdk"


export type NamespaceSearchOptions = Parameters<ReturnType<typeof useNamespacesStore>["search"]>[0]

export class NamespaceIterator extends EntityIterator<Namespace, NamespaceSearchOptions>{
    // oxlint-disable-next-line no-useless-constructor
    constructor(fetchSize: number, options?: NamespaceSearchOptions) {
        super(fetchSize, options)
    }

    async fetchCall() {
        const namespacesStore = useNamespacesStore()
        const result = await namespacesStore.search(this.fetchOptions())
        return {...result, total: result.total ?? 0}
    }
}

export function defaultNamespace() {
    return localStorage.getItem(storageKeys.DEFAULT_NAMESPACE)
}

export default function useNamespaces(fetchSize: number, options?: NamespaceSearchOptions): NamespaceIterator {
    return new NamespaceIterator(fetchSize, options)
}
