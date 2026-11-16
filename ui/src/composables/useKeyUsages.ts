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

function isInNamespaceScope(flowNamespace: string, targetNamespace: string | undefined) {
    return targetNamespace === undefined
        || flowNamespace === targetNamespace || flowNamespace.startsWith(`${targetNamespace}.`)
}

export function selectUsages(results: SourceSearchResult[], namespace: string | undefined): KeyUsage[] {
    return results.flatMap((flow) => {
        const flowInScope = isInNamespaceScope(flow.namespace, namespace)
        return flow.matches
            .map((match) => ({namespace: flow.namespace, id: flow.id, line: match.line, snippet: stripMarkers(match.snippet)}))
            .filter((usage) => flowInScope || (namespace !== undefined && usage.snippet.includes(namespace)))
    })
}

export function useKeyUsages(target: MaybeRefOrGetter<KeyTarget | undefined>) {
    const status = ref<KeyUsagesStatus>("idle")
    const usages = ref<KeyUsage[]>([])
    const total = ref(0)
    const shown = ref(0)
    const errorMessage = ref<string>()

    let generation = 0
    const isCurrent = (gen: number) => gen === generation

    const truncated = computed(() => total.value > shown.value)
    const pattern = computed(() => {
        const current = toValue(target)
        return current ? buildUsagePattern(current) : undefined
    })

    async function load(current: KeyTarget | undefined) {
        const gen = ++generation
        usages.value = []
        total.value = 0
        shown.value = 0
        errorMessage.value = undefined

        if (!current) {
            status.value = "idle"
            return
        }

        status.value = "loading"
        try {
            const response = await FlowsAPI.searchFlowsBySourceCode({
                caseSensitive: true,
                wholeWord: false,
                regex: true,
                scope: "ALL",
                page: 1,
                size: USAGES_PAGE_SIZE,
                q: buildUsagePattern(current),
            })
            if (!isCurrent(gen)) return
            const results = (response.results ?? []) as SourceSearchResult[]
            usages.value = selectUsages(results, current.namespace)
            total.value = response.total ?? results.length
            shown.value = results.length
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
            return current ? `${current.kind}\u0000${current.namespace}\u0000${current.key}` : undefined
        },
        () => load(toValue(target)),
        {immediate: true},
    )

    onScopeDispose(() => {
        generation++
    })

    return {status, usages, total, shown, truncated, errorMessage, pattern}
}
