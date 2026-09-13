import {defineStore} from "pinia"
import {ref} from "vue"
import {useClient} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"

/** The credential shapes the backend accepts, mirroring {@code CredentialType}. */
export type CredentialType = "API_KEY" | "OAUTH2" | "BEARER_TOKEN" | "BASIC_AUTH" | "CERNER_FHIR"

/** A credential as the list returns it: identity only, never material. */
export interface CredentialSummary {
    tenantId?: string;
    namespace: string;
    name: string;
    description?: string;
    type: CredentialType;
    updated?: string;
}

/**
 * A credential in full. Only the fields of its own type are present, so everything past the
 * shared header is optional.
 */
export interface CredentialDetail extends CredentialSummary {
    key?: string;
    bearerToken?: string;
    username?: string;
    password?: string;
    tokenUrl?: string;
    tenantKey?: string;
    clientId?: string;
    clientSecret?: string;
    scopes?: string[];
    fhirVersion?: string;
    sandbox?: boolean;
    fhirBaseUrl?: string;
    conformance?: string;
}

export interface CredentialTypeOption {
    type: CredentialType;
    label: string;
}

/**
 * Stored credentials.
 *
 * Kestra 2.0 has no credentials of its own, so unlike secrets there is no OSS store to extend --
 * this is the whole surface. Writes are namespace-scoped because a credential's identity is its
 * tenant, namespace and name together.
 *
 * Calls go through the raw client rather than the generated SDK, keeping Fethr's endpoints in the
 * override layer so an SDK regeneration never has to know about them.
 */
export const useCredentialsStore = defineStore("credentials", () => {
    const axios = useClient()

    const types = ref<CredentialTypeOption[]>([])

    const base = () => `${apiUrl()}/credentials`
    const namespaced = (namespace: string) => `${apiUrl()}/namespaces/${namespace}/credentials`

    async function search(options: {page?: number; size?: number; sort?: string; filters?: unknown[]} = {}) {
        const {data} = await axios.get(base(), {params: options})
        return data as {results: CredentialSummary[]; total: number}
    }

    /** The types that can be created, with the labels the type picker shows. */
    async function loadTypes() {
        const {data} = await axios.get(`${base()}/types`)
        types.value = data as CredentialTypeOption[]
        return types.value
    }

    /**
     * Reads one credential including its material. The only call that returns secrets, which is
     * why the edit form is the only thing that makes it.
     */
    async function load({namespace, name}: {namespace: string; name: string}) {
        const {data} = await axios.get(`${namespaced(namespace)}/${encodeURIComponent(name)}`)
        return data as CredentialDetail
    }

    async function create({namespace, credential}: {namespace: string; credential: Record<string, unknown>}) {
        const {data} = await axios.post(namespaced(namespace), credential)
        return data as CredentialDetail
    }

    async function update({namespace, name, credential}: {namespace: string; name: string; credential: Record<string, unknown>}) {
        const {data} = await axios.put(`${namespaced(namespace)}/${encodeURIComponent(name)}`, credential)
        return data as CredentialDetail
    }

    async function remove({namespace, name}: {namespace: string; name: string}) {
        await axios.delete(`${namespaced(namespace)}/${encodeURIComponent(name)}`)
    }

    /**
     * Deletes several credentials of one namespace. The server skips names that are already gone
     * and reports the ones it removed, so a stale selection does not fail the whole request.
     */
    async function bulkRemove({namespace, names}: {namespace: string; names: string[]}) {
        const {data} = await axios.delete(namespaced(namespace), {data: {names}})
        return data as {names: string[]}
    }

    return {types, search, loadTypes, load, create, update, remove, bulkRemove}
})
