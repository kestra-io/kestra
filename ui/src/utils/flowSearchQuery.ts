import {decodeSearchParams} from "@kestra-io/design-system"
import type {LocationQuery} from "vue-router"

const AFFIRMATIVE_OPERATIONS = new Set(["PREFIX", "EQUALS", "IN"])

export function parseFlowsSearchQuery(
    query: LocationQuery,
    explicitNamespace?: string,
): {
    searchQuery: string
    filteredNamespace: string | undefined
    sourceSearchQuery: Record<string, string>
} {
    const decodedParams = decodeSearchParams(query)

    const rawQ = decodedParams.find((p) => p.field === "q")?.value ?? (query.q as string | string[] | undefined)
    const searchQuery = Array.isArray(rawQ)
        ? rawQ.filter(Boolean).join(" ").trim()
        : (rawQ ? String(rawQ).trim() : "")

    let filteredNamespace: string | undefined
    if (explicitNamespace) {
        filteredNamespace = explicitNamespace
    } else {
        const decodedNamespace = decodedParams.find((p) => p.field === "namespace")
        if (
            decodedNamespace?.value &&
            (!decodedNamespace.operation || AFFIRMATIVE_OPERATIONS.has(decodedNamespace.operation))
        ) {
            const rawNamespace = decodedNamespace.value
            if (Array.isArray(rawNamespace)) {
                filteredNamespace = rawNamespace[0] ? String(rawNamespace[0]) : undefined
            } else {
                filteredNamespace = String(rawNamespace).split(",")[0] || undefined
            }
        }
    }

    const sourceSearchQuery: Record<string, string> = {q: searchQuery}
    if (filteredNamespace) {
        sourceSearchQuery.namespace = filteredNamespace
    }

    return {
        searchQuery,
        filteredNamespace,
        sourceSearchQuery,
    }
}
