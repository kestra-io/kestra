import {defineStore} from "pinia"
import {ref, computed} from "vue"

import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import * as FilesAPI from "@kestra-io/kestra-sdk/files"
import * as KvAPI from "@kestra-io/kestra-sdk/kv"
import * as SecretsAPI from "@kestra-io/kestra-sdk/secrets"
import * as NamespacesAPI from "@kestra-io/kestra-sdk/namespaces"
import type {SourceSearchScope} from "@kestra-io/kestra-sdk"

import {groupByNamespace, type CrossSearchSelection, type SearchResourceType, type SearchStatus} from "../utils/crossResourceSearch"
import type {SourceSearchResult} from "../utils/sourceSearchDiff"

const SEARCH_PAGE_SIZE = 200

/**
 * Namespace files are fetched one request per namespace; a tenant with hundreds of namespaces would
 * otherwise open hundreds of connections at once on every keystroke that survives the debounce.
 */
const NAMESPACE_FETCH_CONCURRENCY = 8

const isTruncated = (total: number | undefined, shown: number) => total !== undefined && shown < total

async function runBounded<T>(items: T[], limit: number, task: (item: T) => Promise<void>) {
    let cursor = 0
    const workers = Array.from({length: Math.min(limit, items.length)}, async () => {
        while (cursor < items.length) {
            await task(items[cursor++])
        }
    })
    await Promise.all(workers)
}

export interface NamespaceFileState {
    namespace: string;
    status: "pending" | "done" | "failed";
    paths: string[];
    errorMessage?: string;
}

export interface KvMatchEntry {
    key: string;
    updateDate?: string;
    creationDate?: string;
    expirationDate?: string;
}

export interface SecretMatchEntry {
    key: string;
}

export interface ResourceGroup<M> {
    namespace: string;
    matches: M[];
}

interface FlowsTypeState {
    status: SearchStatus;
    results: SourceSearchResult[];
    total?: number;
    errorMessage?: string;
}

interface FilesTypeState {
    status: SearchStatus;
    namespaces: NamespaceFileState[];
    /** The query these namespaces were fanned out for, so a retry cannot run under a different one. */
    query?: string;
    errorMessage?: string;
}

interface KvTypeState {
    status: SearchStatus;
    groups: ResourceGroup<KvMatchEntry>[];
    total?: number;
    errorMessage?: string;
}

interface SecretsTypeState {
    status: SearchStatus;
    groups: ResourceGroup<SecretMatchEntry>[];
    total?: number;
    errorMessage?: string;
}

export interface FlowsSearchParams {
    query: string;
    namespace?: string;
    caseSensitive: boolean;
    wholeWord: boolean;
    regex: boolean;
    scope: SourceSearchScope;
}

export interface CrossResourceSearchParams {
    types: SearchResourceType[];
    query: string;
    namespace?: string;
    caseSensitive: boolean;
    wholeWord: boolean;
    regex: boolean;
    scope: SourceSearchScope;
}

export const useCrossResourceSearchStore = defineStore("crossResourceSearch", () => {
    const flows = ref<FlowsTypeState>({status: "idle", results: []})
    const files = ref<FilesTypeState>({status: "idle", namespaces: []})
    const kv = ref<KvTypeState>({status: "idle", groups: []})
    const secrets = ref<SecretsTypeState>({status: "idle", groups: []})

    /**
     * Every search run gets a generation; a resolution only writes to the shared state while its
     * generation is still the newest. The consumer debounces but lodash's trailing edge only delays
     * invocation, so a slow earlier run can still resolve after a later one and clobber its results.
     */
    let generation = 0
    const nextGeneration = () => ++generation
    const isCurrent = (gen: number) => gen === generation

    function reset() {
        nextGeneration()
        flows.value = {status: "idle", results: []}
        files.value = {status: "idle", namespaces: []}
        kv.value = {status: "idle", groups: []}
        secrets.value = {status: "idle", groups: []}
    }

    async function searchFlows(params: FlowsSearchParams, gen: number = nextGeneration()) {
        if (!params.query) {
            flows.value = {status: "idle", results: []}
            return
        }

        flows.value = {status: "counting", results: flows.value.results}
        try {
            const response = await FlowsAPI.searchFlowsBySourceCode({
                caseSensitive: params.caseSensitive,
                wholeWord: params.wholeWord,
                regex: params.regex,
                scope: params.scope,
                page: 1,
                size: SEARCH_PAGE_SIZE,
                q: params.query,
                namespace: params.namespace,
            })
            if (!isCurrent(gen)) return
            flows.value = {status: "done", results: (response.results ?? []) as SourceSearchResult[], total: response.total}
        } catch (e: any) {
            if (!isCurrent(gen)) return
            flows.value = {status: "failed", results: [], errorMessage: e?.response?.data?.message ?? e?.message}
        }
    }

    function setNamespaceFileState(state: NamespaceFileState, gen: number) {
        if (!isCurrent(gen)) return

        const next = [...files.value.namespaces]
        const index = next.findIndex((n) => n.namespace === state.namespace)
        if (index === -1) next.push(state)
        else next[index] = state

        const anyPending = next.some((n) => n.status === "pending")
        files.value = {status: anyPending ? "counting" : "done", query: files.value.query, namespaces: next}
    }

    async function fetchNamespaceFiles(namespace: string, query: string, gen: number) {
        try {
            const paths = await FilesAPI.searchNamespaceFiles({namespace, q: query}) ?? []
            setNamespaceFileState({namespace, status: "done", paths}, gen)
        } catch (e: any) {
            setNamespaceFileState({namespace, status: "failed", paths: [], errorMessage: e?.response?.data?.message ?? e?.message}, gen)
        }
    }

    /**
     * Namespace files have no bulk "search all namespaces" endpoint — fanning out is bounded to the
     * namespaces the user can list (or a single one when a namespace filter is active), and each
     * namespace's failure is isolated so one timing out never blanks the others.
     */
    async function searchFiles(params: {query: string; namespace?: string}, gen: number = nextGeneration()) {
        if (!params.query) {
            files.value = {status: "idle", namespaces: []}
            return
        }

        let namespaces: string[]
        try {
            namespaces = params.namespace
                ? [params.namespace]
                : (await NamespacesAPI.autocompleteNamespaces({existingOnly: true}) as unknown as string[] ?? [])
        } catch (e: any) {
            if (!isCurrent(gen)) return
            files.value = {status: "failed", namespaces: [], errorMessage: e?.response?.data?.message ?? e?.message}
            return
        }

        if (!isCurrent(gen)) return

        files.value = {
            status: namespaces.length === 0 ? "done" : "counting",
            query: params.query,
            namespaces: namespaces.map((namespace) => ({namespace, status: "pending" as const, paths: []})),
        }

        await runBounded(namespaces, NAMESPACE_FETCH_CONCURRENCY, (namespace) => fetchNamespaceFiles(namespace, params.query, gen))
    }

    /**
     * A retry belongs to the run that produced the failed namespace, so it reuses that run's query
     * and generation. Taking the query from the caller instead would let a retry fired after the user
     * edited the search box — but before the debounce re-searches — write results for the new query
     * into a list still showing the old one.
     */
    async function retryNamespaceFiles(namespace: string) {
        const query = files.value.query
        if (!query) return

        const gen = generation
        setNamespaceFileState({namespace, status: "pending", paths: []}, gen)
        await fetchNamespaceFiles(namespace, query, gen)
    }

    async function searchKv(params: {query: string; namespace?: string}, gen: number = nextGeneration()) {
        if (!params.query) {
            kv.value = {status: "idle", groups: []}
            return
        }

        kv.value = {status: "counting", groups: kv.value.groups}
        try {
            const filters: any[] = [{field: "q", operation: "EQUALS", value: params.query}]
            if (params.namespace) filters.push({field: "namespace", operation: "EQUALS", value: params.namespace})

            const response = await KvAPI.listAllKeys({filters, page: 1, size: SEARCH_PAGE_SIZE})
            if (!isCurrent(gen)) return
            const results = (response.results ?? []) as {namespace?: string; key?: string; updateDate?: string; creationDate?: string; expirationDate?: string}[]
            kv.value = {
                status: "done",
                total: response.total,
                groups: groupByNamespace(results, (entry) => entry.namespace ?? "", (entry) => ({
                    key: entry.key ?? "",
                    updateDate: entry.updateDate,
                    creationDate: entry.creationDate,
                    expirationDate: entry.expirationDate,
                })),
            }
        } catch (e: any) {
            if (!isCurrent(gen)) return
            kv.value = {status: "failed", groups: [], errorMessage: e?.response?.data?.message ?? e?.message}
        }
    }

    async function searchSecrets(params: {query: string; namespace?: string}, gen: number = nextGeneration()) {
        if (!params.query) {
            secrets.value = {status: "idle", groups: []}
            return
        }

        secrets.value = {status: "counting", groups: secrets.value.groups}
        try {
            const filters: any[] = [{field: "q", operation: "EQUALS", value: params.query}]
            if (params.namespace) filters.push({field: "namespace", operation: "EQUALS", value: params.namespace})

            const response = await SecretsAPI.listSecrets({filters, page: 1, size: SEARCH_PAGE_SIZE})
            if (!isCurrent(gen)) return
            const results = (response.results ?? []) as {namespace?: string; key: string}[]
            secrets.value = {
                status: "done",
                total: response.total,
                groups: groupByNamespace(results, (entry) => entry.namespace ?? "", (entry) => ({key: entry.key})),
            }
        } catch (e: any) {
            if (!isCurrent(gen)) return
            secrets.value = {status: "failed", groups: [], errorMessage: e?.response?.data?.message ?? e?.message}
        }
    }

    async function search(params: CrossResourceSearchParams) {
        const gen = nextGeneration()
        const tasks: Promise<void>[] = []

        if (params.types.includes("flows")) {
            tasks.push(searchFlows({
                query: params.query,
                namespace: params.namespace,
                caseSensitive: params.caseSensitive,
                wholeWord: params.wholeWord,
                regex: params.regex,
                scope: params.scope,
            }, gen))
        } else {
            flows.value = {status: "idle", results: []}
        }

        if (params.types.includes("files")) {
            tasks.push(searchFiles({query: params.query, namespace: params.namespace}, gen))
        } else {
            files.value = {status: "idle", namespaces: []}
        }

        if (params.types.includes("kv")) {
            tasks.push(searchKv({query: params.query, namespace: params.namespace}, gen))
        } else {
            kv.value = {status: "idle", groups: []}
        }

        if (params.types.includes("secrets")) {
            tasks.push(searchSecrets({query: params.query, namespace: params.namespace}, gen))
        } else {
            secrets.value = {status: "idle", groups: []}
        }

        await Promise.all(tasks)
    }

    const flowsMatchCount = computed(() => flows.value.results.reduce((sum, group) => sum + group.matches.length, 0))
    const flowsResourceCount = computed(() => flows.value.results.length)

    const filesMatchCount = computed(() => files.value.namespaces.reduce((sum, n) => sum + n.paths.length, 0))
    const filesResourceCount = computed(() => files.value.namespaces.filter((n) => n.paths.length > 0).length)
    const filesNamespacesTotal = computed(() => files.value.namespaces.length)
    const filesNamespacesDone = computed(() => files.value.namespaces.filter((n) => n.status !== "pending").length)
    const filesNamespacesFailed = computed(() => files.value.namespaces.filter((n) => n.status === "failed"))

    const kvMatchCount = computed(() => kv.value.groups.reduce((sum, group) => sum + group.matches.length, 0))
    const kvResourceCount = computed(() => kv.value.groups.length)

    const secretsMatchCount = computed(() => secrets.value.groups.reduce((sum, group) => sum + group.matches.length, 0))
    const secretsResourceCount = computed(() => secrets.value.groups.length)

    const totalMatchCount = computed(() => flowsMatchCount.value + filesMatchCount.value + kvMatchCount.value + secretsMatchCount.value)
    const totalResourceCount = computed(() => flowsResourceCount.value + filesResourceCount.value + kvResourceCount.value + secretsResourceCount.value)
    const activeTypeCount = computed(() => [flowsMatchCount.value, filesMatchCount.value, kvMatchCount.value, secretsMatchCount.value].filter((count) => count > 0).length)

    function statusFor(type: SearchResourceType): SearchStatus {
        switch (type) {
            case "flows": return flows.value.status
            case "files": return files.value.status
            case "kv": return kv.value.status
            case "secrets": return secrets.value.status
        }
    }

    function countFor(type: SearchResourceType): number {
        switch (type) {
            case "flows": return flowsMatchCount.value
            case "files": return filesMatchCount.value
            case "kv": return kvMatchCount.value
            case "secrets": return secretsMatchCount.value
        }
    }

    /**
     * The list endpoints cap at SEARCH_PAGE_SIZE, so a broad query silently returns a subset. Flows
     * is the one replaceable type, and a bulk replace over a truncated set would only rewrite part of
     * what the user believes they selected — so the caller has to be able to say so.
     */
    function truncatedFor(type: SearchResourceType): boolean {
        switch (type) {
            case "flows": return isTruncated(flows.value.total, flows.value.results.length)
            case "kv": return isTruncated(kv.value.total, kvMatchCount.value)
            case "secrets": return isTruncated(secrets.value.total, secretsMatchCount.value)
            case "files": return false
        }
    }

    function totalFor(type: SearchResourceType): number | undefined {
        switch (type) {
            case "flows": return flows.value.total
            case "kv": return kv.value.total
            case "secrets": return secrets.value.total
            case "files": return undefined
        }
    }

    function resourceCountFor(type: SearchResourceType): number {
        switch (type) {
            case "flows": return flowsResourceCount.value
            case "files": return filesResourceCount.value
            case "kv": return kvResourceCount.value
            case "secrets": return secretsResourceCount.value
        }
    }

    function errorMessageFor(type: SearchResourceType): string | undefined {
        switch (type) {
            case "flows": return flows.value.errorMessage
            case "files": return files.value.errorMessage
            case "kv": return kv.value.errorMessage
            case "secrets": return secrets.value.errorMessage
        }
    }

    const flatSelections = computed<CrossSearchSelection[]>(() => {
        const list: CrossSearchSelection[] = []

        for (const group of flows.value.results) {
            for (const match of group.matches) {
                list.push({type: "flows", namespace: group.namespace, id: group.id, line: match.line, column: match.column})
            }
        }
        for (const namespaceState of files.value.namespaces) {
            for (const path of namespaceState.paths) {
                list.push({type: "files", namespace: namespaceState.namespace, path})
            }
        }
        for (const group of kv.value.groups) {
            for (const match of group.matches) {
                list.push({type: "kv", namespace: group.namespace, key: match.key})
            }
        }
        for (const group of secrets.value.groups) {
            for (const match of group.matches) {
                list.push({type: "secrets", namespace: group.namespace, key: match.key})
            }
        }

        return list
    })

    return {
        flows,
        files,
        kv,
        secrets,
        search,
        searchFlows,
        searchFiles,
        searchKv,
        searchSecrets,
        retryNamespaceFiles,
        reset,
        flowsMatchCount,
        flowsResourceCount,
        filesMatchCount,
        filesResourceCount,
        filesNamespacesTotal,
        filesNamespacesDone,
        filesNamespacesFailed,
        kvMatchCount,
        kvResourceCount,
        secretsMatchCount,
        secretsResourceCount,
        totalMatchCount,
        totalResourceCount,
        activeTypeCount,
        statusFor,
        countFor,
        resourceCountFor,
        errorMessageFor,
        truncatedFor,
        totalFor,
        flatSelections,
    }
})
