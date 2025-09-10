import {EntityIterator, FetchResult} from "./entityIterator.ts";
import {NamespaceIterator} from "./useNamespaces.ts";
import {Me} from "override/stores/auth";
import {useNamespacesStore} from "override/stores/namespaces.ts";
import permissions from "../models/permission";
import actions from "../models/action";
import {ref} from "vue";

export interface NamespaceSecret {
    key: string;
    description: string;
    tags: {key: string, value: string}[];
}

export type SecretIterator = NamespaceSecretIterator | AllSecretIterator;

type NamespaceSecretFetchResult = FetchResult<NamespaceSecret> & { readOnly: boolean };

export class NamespaceSecretIterator extends EntityIterator<NamespaceSecret>{
    readonly namespace: string;
    areNamespaceSecretsReadOnly = ref(undefined) as unknown as boolean | undefined;

    constructor(namespace: string, fetchSize: number, options?: any) {
        super(fetchSize, options);
        this.namespace = namespace;
    }

    fetchOptions(): any {
        return {
            ...super.fetchOptions(),
            id: this.namespace,
            sort: "key:asc",
            ...this.options
        };
    }

    fetchCall(): Promise<NamespaceSecretFetchResult> {
        return this.doFetch();
    }

    private async doFetch(): Promise<NamespaceSecretFetchResult> {
        const namespacesStore = useNamespacesStore();
        const fetch = (await namespacesStore.listSecrets(this.fetchOptions())) as NamespaceSecretFetchResult;
        this.areNamespaceSecretsReadOnly = fetch.readOnly ?? true;

        return {
            ...fetch,
            results: fetch.results.map(secret => ({...secret, namespace: this.namespace}))
        };
    }
}

export class AllSecretIterator extends EntityIterator<NamespaceSecret>{
    private readonly user: Me;
    private namespaceIterator: NamespaceIterator | undefined;
    private namespaceSecretIterator: NamespaceSecretIterator | undefined;
    private areNamespaceSecretsReadOnly = ref({}) as unknown as {[namespace: string]: boolean};

    constructor(user: Me, fetchSize: number, options?: any) {
        super(fetchSize, options);
        this.user = user;
    }

    stopCondition(): boolean {
        return this.total === 0;
    }

    async fetchCall(): Promise<FetchResult<NamespaceSecret>> {
        if (this.namespaceIterator === undefined) {
            this.namespaceIterator = new NamespaceIterator(20, {
                commit: false,
                sort: "id:asc"
            });
        }

        while (true) {
            if (this.namespaceSecretIterator === undefined) {
                const namespace = await this.namespaceIterator.single();
                if (namespace === undefined) {
                    return Promise.resolve({total: 0, results: [], readOnly: false});
                }

                if (!this.user.hasAnyAction(permissions.SECRET, actions.READ, namespace.id)) {
                    continue;
                }

                this.namespaceSecretIterator = new NamespaceSecretIterator(namespace.id, this.fetchSize, this.options);
            }

            const fetch = {
                results: await this.namespaceSecretIterator.next(),
                namespace: this.namespaceSecretIterator.namespace,
                areNamespaceSecretsReadOnly: this.namespaceSecretIterator.areNamespaceSecretsReadOnly,
                total: this.namespaceSecretIterator.total
            };

            if (this.namespaceSecretIterator.stopCondition()) {
                this.namespaceSecretIterator = undefined;
            }

            if (fetch.results.length > 0) {
                this.areNamespaceSecretsReadOnly[fetch.namespace] = fetch.areNamespaceSecretsReadOnly!;
                return {
                    total: fetch.total!,
                    results: fetch.results.map(secret => ({...secret, namespace: fetch.namespace}))
                };
            }
        }
    }
}

export function useNamespaceSecrets(namespace: string, secretsFetchSize: number, options?: any): NamespaceSecretIterator {
    return new NamespaceSecretIterator( namespace, secretsFetchSize, options);
}

export function useAllSecrets(user: Me, secretsFetchSize: number, options?: any): AllSecretIterator {
    return new AllSecretIterator(user, secretsFetchSize, options);
}