import {ref} from "vue"
import {apiUrl} from "override/utils/route"
import * as Utils from "../utils/utils"
import {useClient, type PagedResultsNamespace} from "@kestra-io/kestra-sdk"
import * as NamespaceAPI from "@kestra-io/kestra-sdk/namespaces"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import * as KvAPI from "@kestra-io/kestra-sdk/kv"
import * as FilesAPI from "@kestra-io/kestra-sdk/files"
import * as SecretsAPI from "@kestra-io/kestra-sdk/secrets"

export {PagedResultsNamespace}

function base(namespace: string) {
    return `${apiUrl()}/namespaces/${namespace}`
}

const slashPrefix = (path: string) => (path.startsWith("/") ? path : `/${path}`)
const safePath = (path: string) => encodeURIComponent(path).replace(/%2C|%2F/g, "/")
export const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 404}

export const useBaseNamespacesStore = () => {
    const namespace = ref<any>(undefined)
    const inheritedSecrets = ref<any>(undefined)
    const inheritedKVs = ref<any>(undefined)
    const inheritedKVModalVisible = ref(false)
    const addKvModalVisible = ref(false)
    const autocomplete = ref<string[]>()
    const existing = ref(true)

    const axios = useClient()

    async function loadAutocomplete(options?: {q?: string, ids?: string[], existingOnly?: boolean}) {
        const response = await NamespaceAPI.autocompleteNamespaces({existingOnly: false, ...options})
        autocomplete.value = response
        return response
    }

    async function search(options: {commit?: boolean, sort?: string, [key: string]: any}): Promise<PagedResultsNamespace> {
        const {commit: _commit, sort, ...rest} = options

        const data = await NamespaceAPI.searchNamespaces({...rest, sort: sort ? [sort] : undefined})
        return data
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
        const data = await FlowsAPI.flowDependenciesFromNamespace(options)
        return {data}
    }

    async function kvsList(item: {id: string}) {
        const data = await KvAPI.listAllKeys({filters: [{field: "namespace", operation: "EQUALS", value: item.id}] as any})
        return data?.results
    }

    async function kv(payload: {namespace: string; key: string}) {
        return KvAPI.keyValue(payload)
    }

    async function loadInheritedKVs(id: string) {
        inheritedKVs.value = await KvAPI.listKeysWithInheritence({namespace: id})
    }

    async function createKv(payload: {namespace: string; key: string; value: any; contentType: string; description: string; ttl?: string}) {
        await KvAPI.setKeyValue(
            {namespace: payload.namespace, key: payload.key, body: payload.value},
            {headers: {"Content-Type": payload.contentType, "description": payload.description, "ttl": payload.ttl}} as any,
        )
    }

    async function deleteKv(payload: {namespace: string; key: string}) {
        await KvAPI.deleteKeyValue(payload)
    }

    async function deleteKvs(payload: {namespace: string; request: any}) {
        await KvAPI.deleteKeyValues({namespace: payload.namespace, ...payload.request})
    }

    async function loadInheritedSecrets({id, commit: shouldCommit}: {id: string; commit: boolean | undefined; [key: string]: any}): Promise<Record<string, string[]>> {
        let data: Record<string, string[]>
        try {
            data = await NamespaceAPI.inheritedSecrets({namespace: id})
        } catch (e: any) {
            if (e.status === 404) {
                data = {[id]: []}
            } else {
                throw e
            }
        }
        if (shouldCommit !== false) {
            inheritedSecrets.value = data
        }
        return data
    }

    async function listSecrets({id}: {id: string; commit: boolean | undefined; [key: string]: any}): Promise<{total: number, results: {key: string, description?: string, tags?: {key: string, value: string}[]}[], readOnly?: boolean}> {
        try {
            const data = await SecretsAPI.listSecrets({filters: [{field: "namespace", operation: "EQUALS", value: id}] as any}) as any
            return data
        } catch (e: any) {
            if (e.status === 404) return {total: 0, results: [], readOnly: false}
            throw e
        }
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

    async function createDirectory(payload: {namespace: string; path: string}) {
        await FilesAPI.createNamespaceDirectory(payload)
    }

    async function readDirectory<T>(payload: {namespace: string; path?: string}): Promise<T[]> {
        try {
            const data = await FilesAPI.listNamespaceDirectoryFiles(payload)
            return (data ?? []) as unknown as T[]
        } catch (e: any) {
            if (e.status === 404) {
                const notFoundError: any = new Error("Directory not found")
                notFoundError.status = 404
                throw notFoundError
            }
            throw e
        }
    }

    async function createFile(payload: {namespace: string; path: string; content: string}) {
        const DATA = new FormData()
        const BLOB = new Blob([payload.content], {type: "text/plain"})
        DATA.append("fileContent", BLOB)

        const URL = `${base(payload.namespace)}/files?path=${slashPrefix(payload.path)}`
        // Don't set Content-Type - the browser must generate the multipart boundary itself.
        await axios.post(URL, Utils.toFormData(DATA))
    }

    async function fileRevisions(payload: {namespace: string; path: string}): Promise<{revision: number}[]> {
        if (!payload.path) return []

        try {
            return await FilesAPI.fileRevisions(payload) as unknown as {revision: number}[]
        } catch (e: any) {
            console.error(e.message ?? "File not found")
            return []
        }
    }

    async function readFile(payload: {namespace: string; path: string, revision?: number}): Promise<{content?: string, notFound?: boolean, error?: string}> {
        if (!payload.path) return {error: "Path is required"}

        try {
            const blob = await FilesAPI.fileContent(payload)
            return {content: await blob.text() ?? ""}
        } catch (e: any) {
            if (e.status === 404) {
                return {notFound: true, error: e.message ?? "File not found"}
            }
            throw e
        }
    }

    async function searchFiles(payload: {namespace: string; query: string}) {
        return await FilesAPI.searchNamespaceFiles({namespace: payload.namespace, q: payload.query}) ?? []
    }

    async function importFileDirectory(payload: {namespace: string; path: string; content: ArrayBuffer}) {
        const DATA = new FormData()
        const BLOB = new Blob([payload.content], {type: "text/plain"})
        DATA.append("fileContent", BLOB)

        const URL = `${base(payload.namespace)}/files?path=${slashPrefix(safePath(payload.path))}`
        // Don't set Content-Type - the browser must generate the multipart boundary itself.
        await axios.post(URL, DATA)
    }

    async function moveFileDirectory(payload: {namespace: string; old: string; new: string}) {
        await FilesAPI.moveFileDirectory({namespace: payload.namespace, from: payload.old, to: payload.new})
    }

    async function renameFileDirectory(payload: {namespace: string; old: string; new: string}) {
        await FilesAPI.moveFileDirectory({namespace: payload.namespace, from: payload.old, to: payload.new})
    }

    async function deleteFileDirectory(payload: {namespace: string; path: string}) {
        await FilesAPI.deleteFileDirectory(payload)
    }

    async function exportFileDirectory(payload: {namespace: string}) {
        const URL = `${base(payload.namespace)}/files/export`
        const request = await axios.get(URL)

        const name = payload.namespace + "_files.zip"
        Utils.downloadUrl(request.request?.responseURL ?? "", name)
    }

    return {
        autocomplete,
        loadAutocomplete,
        search,
        load,
        update,
        loadDependencies,
        existing,
        namespace,
        inheritedSecrets,
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
