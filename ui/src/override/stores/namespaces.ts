import {defineStore} from "pinia"
import {useClient} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"
import {useBaseNamespacesStore} from "../../composables/useBaseNamespaces"

/**
 * Tags as the secrets table models them: a list of key/value pairs, either half possibly still
 * blank while the row is being typed.
 */
type SecretTag = {key?: string; value?: string}

type SecretPayload = {
    key: string;
    value?: string;
    description?: string;
    tags?: SecretTag[];
}

/**
 * Secrets are written through the namespace that owns them.
 *
 * Kestra 2.0 ships the secrets screen fully built but inert in OSS: it calls createSecrets,
 * patchSecret and deleteSecrets, and OSS supplies no implementation because storing secrets is
 * what its secret-manager implementations do. Fethr stores secrets in Kestra's own database, so
 * these are the calls that make that screen live.
 *
 * They go through the raw client rather than the generated SDK: the SDK is regenerated from the
 * OpenAPI spec, and keeping Fethr's endpoints in the override layer means a regeneration never has
 * to know about them. `createDirectory` and the file upload in the OSS store take the same route.
 */
export const useNamespacesStore = defineStore("namespaces", () => {
    const ossStore = useBaseNamespacesStore()
    const axios = useClient()

    const secretsUrl = (namespace: string) => `${apiUrl()}/namespaces/${namespace}/secrets`

    /**
     * Creates a secret, or replaces the value of one that already exists.
     *
     * The secrets table sends the same call whether the drawer was opened to add a secret or to
     * rotate one, so this is an upsert rather than a create.
     */
    async function createSecrets({namespace, secret}: {namespace: string; secret: SecretPayload}) {
        const {data} = await axios.put(secretsUrl(namespace), secret)
        return data
    }

    /**
     * Edits a secret's description and tags, leaving the value alone, so a secret can be relabelled
     * by someone who does not know it.
     */
    async function patchSecret({namespace, secret}: {namespace: string; secret: SecretPayload}) {
        const {data} = await axios.patch(secretsUrl(namespace), secret)
        return data
    }

    async function deleteSecrets({namespace, key}: {namespace: string; key: string}) {
        const {data} = await axios.delete(`${secretsUrl(namespace)}/${encodeURIComponent(key)}`)
        return data
    }

    /**
     * Deletes several secrets of one namespace. The server skips keys that are already gone and
     * reports the ones it removed, so a stale selection does not fail the whole request.
     */
    async function bulkDeleteSecrets({namespace, keys}: {namespace: string; keys: string[]}) {
        const {data} = await axios.delete(secretsUrl(namespace), {data: {keys}})
        return data as {keys: string[]}
    }

    /**
     * Fetches a secret's plaintext. Deliberately a separate call from anything that lists or reads
     * metadata, so a value only ever reaches the browser when someone asked to see it.
     */
    async function revealSecret({namespace, key}: {namespace: string; key: string}) {
        const {data} = await axios.get(`${secretsUrl(namespace)}/${encodeURIComponent(key)}/value`)
        return data as string
    }

    return {
        ...ossStore,
        createSecrets,
        patchSecret,
        deleteSecrets,
        bulkDeleteSecrets,
        revealSecret,
    }
})
