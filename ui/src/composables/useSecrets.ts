import {Store} from "vuex";
import {EntityIterator} from "./entityIterator.ts";
import {NamespaceIterator} from "./useNamespaces.ts";
import {Me} from "../stores/auth";
import permissions from "../models/permission";
import actions from "../models/action";
import {ref} from "vue";

export interface NamespaceSecret {
    key: string;
    description: string;
    tags: {key: string, value: string}[];
}

export type SecretIterator = NamespaceSecretIterator | AllSecretIterator;

export class NamespaceSecretIterator extends EntityIterator<NamespaceSecret>{
    private readonly store: Store<any>;
    private readonly namespace: string;
    areNamespaceSecretsReadOnly: boolean | undefined = ref(undefined);

    constructor(store: Store<any>, namespace: string, fetchSize: number, options?: any) {
        super(fetchSize, options);
        this.store = store;
        this.namespace = namespace;
    }

    async checkNamespaceSecretsAreReadOnly() {
        if (this.areNamespaceSecretsReadOnly === undefined) {
            await this.doFetch();
        }

        return this.areNamespaceSecretsReadOnly;
    }

    fetchOptions(): any {
        return {
            ...super.fetchOptions(),
            id: this.namespace,
            sort: "key:asc",
            ...this.options
        };
    }

    fetchCall(): Promise<{ total: number; results: NamespaceSecret[], readOnly: boolean }> {
        return this.doFetch();
    }

    private async doFetch(): Promise<{ total: number; results: NamespaceSecret[], readOnly: boolean }> {
        const fetch = await this.store.dispatch("namespace/secrets", this.fetchOptions());
        this.areNamespaceSecretsReadOnly = fetch.readOnly ?? true;

        return {
            ...fetch,
            results: fetch.results.map(secret => ({...secret, namespace: this.namespace}))
        };
    }
}

export class AllSecretIterator extends EntityIterator<NamespaceSecret>{
    private readonly store: Store<any>;
    private readonly user: Me;
    private namespaceIterator: NamespaceIterator;
    private namespaceSecretIterator: NamespaceSecretIterator;
    private areNamespaceSecretsReadOnly: {[namespace: string]: boolean} = ref({});

    constructor(store: Store<any>, fetchSize: number, options?: any) {
        super(fetchSize, options);
        this.store = store;
        this.user = this.store.state?.["auth"]?.user;
    }

    async checkNamespaceSecretsAreReadOnly(namespace: string): Promise<boolean> {
        if (this.areNamespaceSecretsReadOnly?.[namespace] === undefined) {
            this.areNamespaceSecretsReadOnly[namespace] = await new NamespaceSecretIterator(this.store, namespace, 1).areNamespaceSecretsReadOnly();
        }
        return Promise.resolve(this.areNamespaceSecretsReadOnly[namespace]);
    }

    async fetchCall(): Promise<{ total: number; results: NamespaceSecret[], readOnly: boolean }> {
        if (this.namespaceIterator === undefined) {
            this.namespaceIterator = new NamespaceIterator(this.store, 20, this.fetchOptions());
        }

        while (this.namespaceSecretIterator === undefined) {
            const namespace = await this.namespaceIterator.single();

            if (namespace === undefined) {
                return Promise.resolve({total: 0, results: [], readOnly: false});
            }

            if (!this.user.hasAnyAction(permissions.SECRET, actions.READ, namespace.id)) {
                continue;
            }

            this.namespaceSecretIterator = new NamespaceSecretIterator(this.store, namespace.id, this.fetchSize, this.options);
            const fetch = await this.namespaceSecretIterator.fetchCall();
            if (fetch.results.length > 0) {
                this.areNamespaceSecretsReadOnly[namespace.id] = fetch.readOnly;
                return {
                    ...fetch,
                    results: fetch.results.map(secret => ({...secret, namespace: namespace.id}))
                };
            }

            this.namespaceSecretIterator = undefined;
        }
    }
}

export function useNamespaceSecrets(store: Store<any>, namespace: string, secretsFetchSize: number, options?: any): NamespaceSecretIterator {
    return new NamespaceSecretIterator(store, namespace, secretsFetchSize, options);
}

export function useAllSecrets(store: Store<any>, secretsFetchSize: number, options?: any): NamespaceSecretIterator {
    return new AllSecretIterator(store, secretsFetchSize, options);
}