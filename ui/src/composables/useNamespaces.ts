import {EntityIterator} from "./entityIterator"
import {useNamespacesStore} from "override/stores/namespaces"
import {storageKeys} from "../utils/constants"
import {Namespace} from "@kestra-io/kestra-sdk"


export class NamespaceIterator extends EntityIterator<Namespace>{
    // oxlint-disable-next-line no-useless-constructor
    constructor(fetchSize: number, options?: any) {
        super(fetchSize, options)
    }

    async fetchCall() {
        const namespacesStore = useNamespacesStore()
        return namespacesStore.search(this.fetchOptions())
    }
}

export function defaultNamespace() {
    return localStorage.getItem(storageKeys.DEFAULT_NAMESPACE)
}

export default function useNamespaces(fetchSize: number, options?: any): NamespaceIterator {
    return new NamespaceIterator(fetchSize, options)
}
