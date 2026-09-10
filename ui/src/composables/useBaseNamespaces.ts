import {ref} from "vue"
import {apiUrl} from "override/utils/route"
import * as Utils from "../utils/utils"
import {useClient, type KvControllerApiDeleteBulkRequest, type KvEntry, type NamespaceLight, type PagedResultsNamespace, type QueryFilter} from "@kestra-io/kestra-sdk"
import * as NamespaceAPI from "@kestra-io/kestra-sdk/namespaces"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import * as KvAPI from "@kestra-io/kestra-sdk/kv"
import * as FilesAPI from "@kestra-io/kestra-sdk/files"
import * as SecretsAPI from "@kestra-io/kestra-sdk/secrets"
import type {KestraRequestOptions} from "../utils/kestraHttp"

export {PagedResultsNamespace}

type NamespaceSearchOptions = Omit<NonNullable<Parameters<typeof NamespaceAPI.searchNamespaces>[0]>, "sort"> & {commit?: boolean; sort?: string}
type SetKeyValueOptions = NonNullable<Parameters<typeof KvAPI.setKeyValue>[1]>

function base(namespace: string) {
    return `${apiUrl()}/namespaces/${namespace}`
}

const slashPrefix = (path: string) => (path.startsWith("/") ? path : `/${path}`)
export const safePath = (path: string) => encodeURIComponent(path).replace(/%2F/g, "/")
export const VALIDATE = {validateStatus: (status: number) => status === 200 || status === 404}

function statusOf(error: unknown): number | undefined {
    if (typeof error !== "object" || error === null || !("status" in error)) return undefined
    const status = error.status
    return typeof status === "number" ? status : undefined
}

function errorMessage(error: unknown): string | undefined {
    if (typeof error !== "object" || error === null || !("message" in error)) return undefined
    const message = error.message
    return typeof message === "string" ? message : undefined
}

export const useBaseNamespacesStore = () => {
    const namespace = ref<NamespaceLight>()
    const inheritedSecrets = ref<Record<string, string[]>>()
    const inheritedKVs = ref<KvEntry[]>()
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

    async function search(options: NamespaceSearchOptions): Promise<PagedResultsNamespace> {
        const {commit: _commit, sort, ...rest} = options

        const data = await NamespaceAPI.searchNamespaces({...rest, sort: sort ? [sort] : undefined})
        return data
    }

    // A missing namespace is reported through `existing` below, so it must not also toast.
    const expectNotFound: KestraRequestOptions = {ignoreNotFound: true}

    let latestLoad = 0

    async function load(id: string) {
        const current = ++latestLoad
        let data: NamespaceLight
        try{
            data = await NamespaceAPI.loadNamespace({id}, expectNotFound)
        }catch (e: unknown) {
            if (statusOf(e) === 404) {
                // A load the user has navigated away from must not report its absence for the
                // namespace they are on, the same way a superseded search is dropped in
                // `stores/logs.ts`.
                if (current === latestLoad) existing.value = false
                return null
            }
            throw e
        }

        if (current !== latestLoad) return data

        namespace.value = data
        existing.value = true

        return namespace.value
    }

    async function update(_: {route: unknown; payload: unknown}) {
        // NOOP IN OSS
    }

    async function loadDependencies(options: {namespace: string}) {
        const data = await FlowsAPI.flowDependenciesFromNamespace(options)
        return {data}
    }

    async function kvsList(item: {id: string}) {
        const filter: QueryFilter = {field: "namespace", operation: "EQUALS", value: item.id}
        const data = await KvAPI.listAllKeys({filters: [filter]})
        return data?.results
    }

    async function kv(payload: {namespace: string; key: string}) {
        return KvAPI.keyValue(payload)
    }

    async function loadInheritedKVs(id: string) {
        inheritedKVs.value = await KvAPI.listKeysWithInheritence({namespace: id})
    }

    async function createKv(payload: {namespace: string; key: string; value: string; contentType: string; description: string; ttl?: string}) {
        await KvAPI.setKeyValue(
            {namespace: payload.namespace, key: payload.key, body: payload.value},
            {headers: {"Content-Type": payload.contentType, "description": payload.description, "ttl": payload.ttl}} as SetKeyValueOptions,
        )
    }

    async function deleteKv(payload: {namespace: string; key: string}) {
        await KvAPI.deleteKeyValue(payload)
    }

    async function deleteKvs(payload: {namespace: string; request: KvControllerApiDeleteBulkRequest}) {
        await KvAPI.deleteKeyValues({namespace: payload.namespace, ...payload.request})
    }

    async function loadInheritedSecrets({id, commit: shouldCommit}: {id: string; commit?: boolean}): Promise<Record<string, string[]>> {
        let data: Record<string, string[]>
        try {
            data = await NamespaceAPI.inheritedSecrets({namespace: id})
        } catch (e: unknown) {
            if (statusOf(e) === 404) {
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

    async function listSecrets({id}: {id: string; commit?: boolean}): Promise<Awaited<ReturnType<typeof SecretsAPI.listSecrets>>> {
        try {
            const filter: QueryFilter = {field: "namespace", operation: "EQUALS", value: id}
            const data = await SecretsAPI.listSecrets({filters: [filter]})
            return data
        } catch (e: unknown) {
            if (statusOf(e) === 404) return {total: 0, results: [], readOnly: false}
            throw e
        }
    }

    async function usableSecrets(this: ReturnType<typeof useBaseNamespacesStore>, id: string): Promise<string[]> {
        return [
            ...Object.values((await this.loadInheritedSecrets({id, commit: false})) ?? {}).flat(),
            ...(await this.listSecrets({id, commit: false})).results.map(({key}) => key),
        ]
    }

    async function createSecrets(_: {namespace: string; secret: unknown}) {
        // NOOP IN OSS
    }

    async function patchSecret(_: {namespace: string; secret: unknown}) {
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
        } catch (e: unknown) {
            if (statusOf(e) === 404) {
                throw Object.assign(new Error("Directory not found"), {status: 404})
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
        } catch (e: unknown) {
            console.error(errorMessage(e) ?? "File not found")
            return []
        }
    }

    async function fileMetadata(payload: {namespace: string; path: string}) {
        return await FilesAPI.fileMetadatas(payload)
    }

    async function readFile(payload: {namespace: string; path: string, revision?: number}): Promise<{content?: string, notFound?: boolean, error?: string}> {
        if (!payload.path) return {error: "Path is required"}

        try {
            const blob = await FilesAPI.fileContent(payload)
            return {content: await blob.text() ?? ""}
        } catch (e: unknown) {
            if (statusOf(e) === 404) {
                return {notFound: true, error: errorMessage(e) ?? "File not found"}
            }
            throw e
        }
    }

    async function searchFiles(payload: {namespace: string; query: string}) {
        return await FilesAPI.searchNamespaceFiles({namespace: payload.namespace, q: payload.query}) ?? []
    }

    /** Sent as a File, not a Blob: the server unpacks only a part named `*.zip`, and a Blob arrives as `filename="blob"`. */
    async function importFileDirectory(payload: {namespace: string; path: string; file: File}) {
        const DATA = new FormData()
        DATA.append("fileContent", payload.file, payload.file.name)

        const URL = `${base(payload.namespace)}/files?path=${slashPrefix(safePath(payload.path))}`
        // Don't set Content-Type - the browser must generate the multipart boundary itself.
        await axios.post(URL, DATA)
    }

    async function moveFileDirectory(payload: {namespace: string; old: string; new: string}) {
        await FilesAPI.moveFileDirectory({namespace: payload.namespace, from: payload.old, to: payload.new})
    }

    /**
     * Unlike {@link moveFileDirectory}, this suppresses the global error toast: the rename caller
     * reports the failure itself, and the two together left a persistent raw
     * "Internal Server Error" alongside the friendly one.
     */
    async function renameFileDirectory(payload: {namespace: string; old: string; new: string}) {
        await FilesAPI.moveFileDirectory(
            {namespace: payload.namespace, from: payload.old, to: payload.new},
            {showMessageOnError: false} as Parameters<typeof FilesAPI.moveFileDirectory>[1],
        )
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
        fileMetadata,
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
