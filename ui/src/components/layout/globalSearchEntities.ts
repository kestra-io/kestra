import type {RouteLocationNamedRaw} from "vue-router"
import type {QueryFilter} from "@kestra-io/kestra-sdk"

export const PALETTE_ENTITY_LIMIT = 5

export type PaletteDestination = "flow" | "namespace"

export type PaletteEntity = {
    destination: PaletteDestination
    title: string
    namespace?: string
    href: RouteLocationNamedRaw
    executionsHref?: RouteLocationNamedRaw
}

export type HighlightPart = {text: string; match: boolean}

type FlowMatch = {id: string; namespace: string}
type NamespaceMatch = {id: string}

const tenantParams = (tenant?: string) => (tenant ? {tenant} : {})

export const flowOrNamespaceFilter = (query: string): QueryFilter => ({
    logical: "or",
    children: [
        {field: "flowId", operation: "CONTAINS", value: query},
        {field: "namespace", operation: "CONTAINS", value: query},
    ],
})

export const namespaceFilter = (query: string): QueryFilter => ({
    field: "namespace",
    operation: "CONTAINS",
    value: query,
})

export const highlightMatch = (text: string, query: string): HighlightPart[] => {
    const needle = query.trim().toLowerCase()
    if (!needle) {
        return [{text, match: false}]
    }

    const haystack = text.toLowerCase()
    const parts: HighlightPart[] = []
    let start = 0
    let index = haystack.indexOf(needle)
    while (index >= 0) {
        if (index > start) {
            parts.push({text: text.slice(start, index), match: false})
        }
        parts.push({text: text.slice(index, index + needle.length), match: true})
        start = index + needle.length
        index = haystack.indexOf(needle, start)
    }
    if (start < text.length) {
        parts.push({text: text.slice(start), match: false})
    }
    return parts.length > 0 ? parts : [{text, match: false}]
}

export const paletteEntities = (
    flows: FlowMatch[],
    namespaces: NamespaceMatch[],
    tenant: string | undefined,
    canOpenExecutions: (namespace: string) => boolean,
): PaletteEntity[] => {
    const flowEntries: PaletteEntity[] = flows.slice(0, PALETTE_ENTITY_LIMIT).map(flow => ({
        destination: "flow",
        title: flow.id,
        namespace: flow.namespace,
        href: {
            name: "flows/update",
            params: {...tenantParams(tenant), namespace: flow.namespace, id: flow.id},
        },
        ...(canOpenExecutions(flow.namespace)
            ? {
                executionsHref: {
                    name: "flows/update/executions",
                    params: {...tenantParams(tenant), namespace: flow.namespace, id: flow.id},
                },
            }
            : {}),
    }))

    const namespaceEntries: PaletteEntity[] = namespaces.slice(0, PALETTE_ENTITY_LIMIT).map(namespace => ({
        destination: "namespace",
        title: namespace.id,
        href: {
            name: "namespaces/update",
            params: {...tenantParams(tenant), id: namespace.id},
        },
    }))

    return [...flowEntries, ...namespaceEntries]
}
