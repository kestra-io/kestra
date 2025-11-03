import {EntityIterator} from "./entityIterator";
import {useNamespacesStore} from "override/stores/namespaces";
import {storageKeys} from "../utils/constants";

export interface Namespace {
    id: string;
    disabled: boolean;
    deleted: boolean;
    description?: string;
}

export class NamespaceIterator extends EntityIterator<Namespace>{
    constructor(fetchSize: number, options?: any) {
        super(fetchSize, options);
    }

    fetchCall(): Promise<{ total: number; results: Namespace[] }> {
        const namespacesStore = useNamespacesStore();
        return namespacesStore.search(this.fetchOptions());
    }
}

export function defaultNamespace() {
    return localStorage.getItem(storageKeys.DEFAULT_NAMESPACE);
}

export default function useNamespaces(fetchSize: number, options?: any): NamespaceIterator {
    return new NamespaceIterator(fetchSize, options);
}
