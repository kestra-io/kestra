import {ref} from "vue"
import {apiUrl} from "override/utils/route"
import * as Utils from "../utils/utils"
import {useClient} from "@kestra-io/kestra-sdk"
import * as NamespaceAPI from "@kestra-io/kestra-sdk/namespaces"

function base(namespace: string) {
    return `${apiUrl()}/namespaces/${namespace}`
}

const HEADERS = {headers: {"Content-Type": "multipart/form-data"}}
const slashPrefix = (path: string) => (path.startsWith("/") ? path : `/${path}`)
const safePath = (path: string) => encodeURIComponent(path).replace(/%2C|%2F/g, "/")
export const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 404}

export const useBaseNamespacesStore = () => {
    const namespace = ref<any>(undefined)
    const namespaces = ref<any[] | undefined>(undefined)
    const secrets = ref<any[] | undefined>(undefined)
    const inheritedSecrets = ref<any>(undefined)
    const kvs = ref<any[] | undefined>(undefined)
    const inheritedKVs = ref<any>(undefined)
    const inheritedKVModalVisible = ref(false)
    const addKvModalVisible = ref(false)
    const autocomplete = ref<string[]>()
    const total = ref(0)
    const existing = ref(true)

    const axios = useClient()

    async function loadAutocomplete(options?: {q?: string, ids?: string[], existingOnly?: boolean}) {
        const response = await NamespaceAPI.autocompleteNamespaces(options ?? {})
        autocomplete.value = response
        return response
    }

    async function search(options: Parameters<typeof NamespaceAPI.searchNamespaces>[0] & {commit?: boolean}) {
        const shouldCommit = options.commit !== false
        delete options.commit
        const response = await NamespaceAPI.searchNamespaces(options)
        if (shouldCommit) {
            namespaces.value = response.results
            total.value = response.total
        }
        return response
    }

    async function load(id: string) {
        try{
            namespace.value = await NamespaceAPI.loadNamespace({id})
        }catch (e: any) {
            if (e.status === 404) {
                existing.value = false
                return null
            }
            throw e
        }

        return namespace.value
    }

    async function update(_: {route: any, payload: any}) {
        // NOOP IN OSS
    }

    async function loadDependencies(options: {namespace: string}) {
        return await axios.get(`${apiUrl()}/namespaces/${options.namespace}/dependencies`)
    }

    async function kvsList(item: {id: string}) {
        const {data} = await axios.get(`${apiUrl()}/kv`, {
            ...VALIDATE,
            params: {
                filters: {namespace: {EQUALS: item.id}},
            },
        })
        return kvs.value = data?.results
    }

    async function kv(payload: {namespace: string; key: string}) {
        const response = await axios.get(`${apiUrl()}/namespaces/${payload.namespace}/kv/${payload.key}`, VALIDATE)
        if (response.status === 404) {
            throw new Error(response.data.message)
        }
        const data = response.data
        const contentLength = response.headers?.["content-length"]

        if (contentLength === (data.length + 2).toString()) {
            return `"${data}"`
        }
        return data
    }

    async function loadInheritedKVs(id: string) {
        const response = await axios.get(`${apiUrl()}/namespaces/${id}/kv/inheritance`, {...VALIDATE})
        inheritedKVs.value = response.data
    }

    async function createKv(payload: {namespace: string; key: string; value: any; contentType: string; description: string; ttl?: string}) {
        await axios.put(
            `${apiUrl()}/namespaces/${payload.namespace}/kv/${payload.key}`,
            payload.value,
            {
                headers: {
                    "Content-Type": payload.contentType,
                    "description": payload.description,
                    "ttl": payload.ttl,
                },
            },
        )
    }

    async function deleteKv(payload: {namespace: string; key: string}) {
        await axios.delete(`${apiUrl()}/namespaces/${payload.namespace}/kv/${payload.key}`)
    }

    async function deleteKvs(payload: {namespace: string; request: any}) {
        await axios.delete(`${apiUrl()}/namespaces/${payload.namespace}/kv`, {
            data: payload.request,
        })
    }

    async function loadInheritedSecrets({id, commit: shouldCommit, ...params}: {id: string; commit: boolean | undefined; [key: string]: any}): Promise<Record<string, string[]>> {
        const response = await axios.get(`${apiUrl()}/namespaces/${id}/inherited-secrets`, {
            ...VALIDATE,
            params,
        })
        if (shouldCommit !== false) {
            inheritedSecrets.value = response.data
        }
        if (response.status === 404) {
            return {[id]: []}
        }
        return response.data
    }

    async function listSecrets({id, commit: shouldCommit, ...params}: {id: string; commit: boolean | undefined; [key: string]: any}): Promise<{total: number, results: {key: string, description?: string, tags?: {key: string, value: string}[]}[], readOnly?: boolean}> {
        const response = await axios.get(`${apiUrl()}/secrets`, {
            ...VALIDATE,
            params: {
                ...params,
                filters: {
                    namespace: {EQUALS: id},
                    ...params.filters,
                },
            },
        })
        if (response.status === 200 && shouldCommit !== false) {
            secrets.value = response.data.results
        }
        if (response.status === 404) {
            return {total: 0, results: [], readOnly: false}
        }
        return response.data
    }

    async function usableSecrets(this: ReturnType<typeof useBaseNamespacesStore>, id: string): Promise<string[]> {
        return [
            ...Object.values((await this.loadInheritedSecrets({id, commit: false})) ?? {}).flat(),
            ...(await this.listSecrets({id, commit: false})).results.map(({key}) => key),
        ]
    }

    async function createSecrets(_: {namespace: string; secret: any}) {
        // NOOP IN OSS
    }

    async function patchSecret(_: {namespace: string; secret: any}) {
        // NOOP IN OSS
    }

    async function deleteSecrets(_: {namespace: string; key: string}) {
        // NOOP IN OSS
    }

    async function loadInheritedVariables(_: {id: string, commit?: boolean}) {
        // NOOP IN OSS
    }

    async function loadInheritedPluginDefaults(_: {id: string, commit?: boolean}) {
        // NOOP IN OSS
    }

    async function createDirectory(payload: {namespace: string; path: string}) {
        const URL = `${base(payload.namespace)}/files/directory?path=${slashPrefix(payload.path)}`
        await axios.post(URL)
    }

    async function readDirectory<T>(payload: {namespace: string; path?: string}): Promise<T[]> {
        const URL = `${base(payload.namespace)}/files/directory${payload.path ? `?path=${slashPrefix(safePath(payload.path))}` : ""}`
        // Accept 200 or 404 so axios doesn't treat 404 as an error (which would set coreStore.error globally)
        const response = await axios.get(URL, VALIDATE)

        // If directory not found, mimic previous behavior (throw) without triggering global 404 page
        if (response.status === 404) {
            const notFoundError: any = new Error("Directory not found")
            notFoundError.status = 404
            throw notFoundError
        }

        return response.data ?? []
    }

    async function createFile(payload: {namespace: string; path: string; content: string}) {
        const DATA = new FormData()
        const BLOB = new Blob([payload.content], {type: "text/plain"})
        DATA.append("fileContent", BLOB)

        const URL = `${base(payload.namespace)}/files?path=${slashPrefix(payload.path)}`
        await axios.post(URL, Utils.toFormData(DATA), HEADERS)
    }

    async function fileRevisions(payload: {namespace: string; path: string}): Promise<{revision: number}[]> {
        if (!payload.path) return []

        const URL = `${base(payload.namespace)}/files/revisions?path=${slashPrefix(safePath(payload.path))}`
        const request = await axios.get(URL, {
            ...VALIDATE,
        })

        if(request.status === 404) {
            const message = JSON.parse(request.data)?.message
            console.error(message ?? "File not found")
            return []
        }

        return (request.data as {revision: number}[])
    }

    async function readFile(payload: {namespace: string; path: string, revision?: number}): Promise<{content?: string, notFound?: boolean, error?: string}> {
        if (!payload.path) return {error: "Path is required"}

        const URL = `${base(payload.namespace)}/files?path=${slashPrefix(safePath(payload.path))}${payload.revision !== undefined ? `&revision=${payload.revision}` : ""}`
        const request = await axios.get<string>(URL, {
            ...VALIDATE,
            transformResponse: (response: any) => response,
            responseType: "json",
        })

        if(request.status === 404) {
            const message = JSON.parse(request.data)?.message
            return {notFound: true, error: message ?? "File not found"}
        }

        return {content: request.data ?? ""}
    }

    async function searchFiles(payload: {namespace: string; query: string}) {
        const URL = `${base(payload.namespace)}/files/search?q=${payload.query}`
        const request = await axios.get(URL)
        return request.data ?? []
    }

    async function importFileDirectory(payload: {namespace: string; path: string; content: ArrayBuffer}) {
        const DATA = new FormData()
        const BLOB = new Blob([payload.content], {type: "text/plain"})
        DATA.append("fileContent", BLOB)

        const URL = `${base(payload.namespace)}/files?path=${slashPrefix(safePath(payload.path))}`
        await axios.post(URL, DATA, HEADERS)
    }

    async function moveFileDirectory(payload: {namespace: string; old: string; new: string}) {
        const URL = `${base(payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`
        await axios.put(URL)
    }

    async function renameFileDirectory(payload: {namespace: string; old: string; new: string}) {
        const URL = `${base(payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`
        await axios.put(URL)
    }

    async function deleteFileDirectory(payload: {namespace: string; path: string}) {
        const URL = `${base(payload.namespace)}/files?path=${slashPrefix(payload.path)}`
        await axios.delete(URL)
    }

    async function exportFileDirectory(payload: {namespace: string}) {
        const URL = `${base(payload.namespace)}/files/export`
        const request = await axios.get(URL)

        const name = payload.namespace + "_files.zip"
        Utils.downloadUrl(request.request.responseURL, name)
    }

    return {
        autocomplete,
        loadAutocomplete,
        search,
        total,
        load,
        update,
        loadDependencies,
        existing,
        namespace,
        namespaces,
        secrets,
        inheritedSecrets,
        kvs,
        inheritedKVModalVisible,
        addKvModalVisible,
        kvsList,
        kv,
        loadInheritedKVs,
        inheritedKVs,
        createKv,
        deleteKv,
        deleteKvs,
        loadInheritedSecrets,
        listSecrets,
        usableSecrets,
        createSecrets,
        patchSecret,
        deleteSecrets,
        loadInheritedVariables,
        loadInheritedPluginDefaults,
        createDirectory,
        readDirectory,
        saveOrCreateFile: createFile,
        readFile,
        fileRevisions,
        searchFiles,
        importFileDirectory,
        moveFileDirectory,
        renameFileDirectory,
        deleteFileDirectory,
        exportFileDirectory,
    }
}
