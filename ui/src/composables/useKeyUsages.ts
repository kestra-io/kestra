import {computed, onScopeDispose, ref, toValue, watch, type MaybeRefOrGetter} from "vue"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import {asProblem} from "@kestra-io/kestra-sdk"

import type {SourceSearchResult} from "../utils/sourceSearchDiff"

const USAGES_PAGE_SIZE = 100

export type KeyKind = "kv" | "secret"

export interface KeyTarget {
    kind: KeyKind;
    key: string;
    namespace?: string;
}

export interface KeyUsage {
    namespace: string;
    id: string;
    line: number;
    snippet: string;
}

export type KeyUsagesStatus = "idle" | "loading" | "done" | "failed"

const REGEX_METACHARACTERS = /[.*+?^${}()|[\]\\]/g

const escapeRegex = (value: string) => value.replace(REGEX_METACHARACTERS, "\\$&")

const stripMarkers = (snippet: string) => snippet.replaceAll("[mark]", "").replaceAll("[/mark]", "").trim()

export function buildUsagePattern({kind, key}: Pick<KeyTarget, "kind" | "key">): string {
    return `\\b${kind}\\(\\s*['"]${escapeRegex(key)}['"]`
}

export function buildLiteralQueries({kind, key}: Pick<KeyTarget, "kind" | "key">): string[] {
    return [`${kind}('${key}'`, `${kind}("${key}"`]
}

export function mergeResults(pages: SourceSearchResult[][]): SourceSearchResult[] {
    const byFlow = new Map<string, SourceSearchResult>()
    for (const flow of pages.flat()) {
        const uid = `${flow.namespace}\u0000${flow.id}`
        const existing = byFlow.get(uid)
        if (!existing) {
            byFlow.set(uid, {...flow, matches: [...flow.matches]})
            continue
        }
        const knownLines = new Set(existing.matches.map((match) => match.line))
        existing.matches.push(...flow.matches.filter((match) => !knownLines.has(match.line)))
        existing.matches.sort((a, b) => a.line - b.line)
    }
    return [...byFlow.values()]
}

function isInNamespaceScope(flowNamespace: string, targetNamespace: string | undefined) {
    return targetNamespace === undefined
        || flowNamespace === targetNamespace || flowNamespace.startsWith(`${targetNamespace}.`)
}

const namesNamespace = (snippet: string, namespace: string) =>
    new RegExp(`['"]${escapeRegex(namespace)}['"]`).test(snippet)

export function selectUsages(results: SourceSearchResult[], namespace: string | undefined): KeyUsage[] {
    return results.flatMap((flow) => {
        const flowInScope = isInNamespaceScope(flow.namespace, namespace)
        return flow.matches
            .map((match) => ({namespace: flow.namespace, id: flow.id, line: match.line, snippet: stripMarkers(match.snippet)}))
            .filter((usage) => flowInScope || (namespace !== undefined && namesNamespace(usage.snippet, namespace)))
    })
}

export function keyTargetId({kind, namespace, key}: KeyTarget): string {
    return `${kind}\u0000${namespace ?? ""}\u0000${key}`
}

export function countFlows(usages: KeyUsage[]): number {
    return new Set(usages.map((usage) => `${usage.namespace}\u0000${usage.id}`)).size
}

export async function fetchKeyUsages(target: KeyTarget): Promise<{usages: KeyUsage[]; truncated: boolean}> {
    const responses = await Promise.all(buildLiteralQueries(target).map((q) => FlowsAPI.searchFlowsBySourceCode({
        caseSensitive: true,
        wholeWord: false,
        regex: false,
        scope: "ALL",
        page: 1,
        size: USAGES_PAGE_SIZE,
        q,
    })))
    const pages = responses.map((response) => (response.results ?? []) as SourceSearchResult[])
    return {
        usages: selectUsages(mergeResults(pages), target.namespace),
        truncated: responses.some((response, index) => (response.total ?? 0) > pages[index].length),
    }
}

export function useKeyUsages(target: MaybeRefOrGetter<KeyTarget | undefined>) {
    const status = ref<KeyUsagesStatus>("idle")
    const usages = ref<KeyUsage[]>([])
    const truncated = ref(false)
    const errorMessage = ref<string>()

    let generation = 0
    const isCurrent = (gen: number) => gen === generation

    const pattern = computed(() => {
        const current = toValue(target)
        return current ? buildUsagePattern(current) : undefined
    })

    async function load(current: KeyTarget | undefined) {
        const gen = ++generation
        usages.value = []
        truncated.value = false
        errorMessage.value = undefined

        if (!current) {
            status.value = "idle"
            return
        }

        status.value = "loading"
        try {
            const result = await fetchKeyUsages(current)
            if (!isCurrent(gen)) return
            usages.value = result.usages
            truncated.value = result.truncated
            status.value = "done"
        } catch (e: unknown) {
            if (!isCurrent(gen)) return
            errorMessage.value = asProblem(e)?.detail ?? (e instanceof Error ? e.message : undefined)
            status.value = "failed"
        }
    }

    watch(
        () => {
            const current = toValue(target)
            return current ? keyTargetId(current) : undefined
        },
        () => load(toValue(target)),
        {immediate: true},
    )

    onScopeDispose(() => {
        generation++
    })

    return {status, usages, truncated, errorMessage, pattern}
}
