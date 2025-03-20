import {Store} from "vuex";

export interface Namespace {
    id: string;
    disabled: boolean;
    deleted: boolean;
}

export class NamespaceIterator {
    private store: Store<any>;
    private readonly fetchSize: number;
    private total: number | undefined;
    private page = 0;
    private options: any;

    constructor(store: Store<any>, fetchSize: number, options?: any) {
        if (fetchSize <= 0) {
            throw new Error("fetchSize must be greater than 0");
        }
        this.store = store;
        this.fetchSize = fetchSize;
        this.options = options ?? {};
    }
    async next(): Promise<string[]> {
        if (this.total !== undefined && this.page * this.fetchSize >= this.total) {
            return Promise.resolve([]);
        }

        const namespacesFetch = await this.store.dispatch("namespace/search", {
            commit: false,
            sort: "id:asc",
            page: ++this.page,
            size: this.fetchSize,
            ...this.options
        });
        this.total = namespacesFetch.total;
        return namespacesFetch.results;
    }

    async all(): Promise<string> {
        const initialFetchedNamespaces = await this.next();
        const namespacesFetchPromises: Promise<Namespace[]> = [];

        for (let i = this.page; i < this.total / this.fetchSize; i++) {
            namespacesFetchPromises.push(this.next());
        }

        return [...initialFetchedNamespaces, ...((await Promise.all(namespacesFetchPromises)).flat())];
    }
}

export default function useNamespaces(store: Store<any>, fetchSize: number, options?: any): NamespaceIterator {
    return new NamespaceIterator(store, fetchSize, options);
}