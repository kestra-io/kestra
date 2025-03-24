import {Store} from "vuex";
import {EntityIterator} from "./entityIterator.ts";

export interface Namespace {
    id: string;
    disabled: boolean;
    deleted: boolean;
}

export class NamespaceIterator extends EntityIterator<Namespace>{
    private readonly store: Store<any>;

    constructor(store: Store<any>, fetchSize: number, options?: any) {
        super(fetchSize, options);
        this.store = store;
    }

    fetchCall(): Promise<{ total: number; results: Namespace[] }> {
        return this.store.dispatch("namespace/search", this.fetchOptions());
    }
}

export default function useNamespaces(store: Store<any>, fetchSize: number, options?: any): NamespaceIterator {
    return new NamespaceIterator(store, fetchSize, options);
}