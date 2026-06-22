import type {Router} from "vue-router"
import {resolveSubflowLinks} from "../../../services/subflowReferences"
import {openFlowInNewTab} from "../../../utils/openFlow"

export const SUBFLOW_LINK_SCHEME = "kestra-subflow"

export interface SubflowTarget {
    namespace: string
    flowId: string
}

export function encodeSubflowTarget(target: SubflowTarget): string {
    return encodeURIComponent(JSON.stringify(target))
}

export function decodeSubflowTarget(query: string): SubflowTarget | undefined {
    try {
        const parsed = JSON.parse(decodeURIComponent(query))
        if (typeof parsed?.namespace === "string" && typeof parsed?.flowId === "string") {
            return {namespace: parsed.namespace, flowId: parsed.flowId}
        }
    } catch {
        return undefined
    }
    return undefined
}

export interface SubflowLinkModel {
    getValue(): string
    getPositionAt(offset: number): {lineNumber: number; column: number}
}

export interface SubflowMonacoLink {
    range: {
        startLineNumber: number
        startColumn: number
        endLineNumber: number
        endColumn: number
    }
    target: {namespace: string; flowId: string}
}

interface LinkResource {
    scheme: string
    query: string
}

export function createSubflowLinkOpener(router: Router) {
    return {
        open(resource: LinkResource): boolean {
            if (resource.scheme !== SUBFLOW_LINK_SCHEME) {
                return false
            }
            const target = decodeSubflowTarget(resource.query)
            if (!target) {
                return false
            }
            openFlowInNewTab({...target, tab: "edit"}, router)
            return true
        },
    }
}

export function buildSubflowLinks(model: SubflowLinkModel): SubflowMonacoLink[] {
    return resolveSubflowLinks(model.getValue()).map((link) => {
        const start = model.getPositionAt(link.range[0])
        const end = model.getPositionAt(link.range[1])
        return {
            range: {
                startLineNumber: start.lineNumber,
                startColumn: start.column,
                endLineNumber: end.lineNumber,
                endColumn: end.column,
            },
            target: link.target,
        }
    })
}

export type NamespaceFlowIdsFetcher = (namespace: string) => Promise<string[]>

export function createFlowExistenceChecker(
    fetch: NamespaceFlowIdsFetcher,
    cacheScope?: () => string,
) {
    const cache = new Map<string, Promise<Set<string>>>()
    const idsForNamespace = (namespace: string): Promise<Set<string>> => {
        const key = JSON.stringify([cacheScope?.() ?? "", namespace])
        let pending = cache.get(key)
        if (!pending) {
            pending = fetch(namespace)
                .then((ids) => new Set(ids))
                .catch(() => new Set<string>())
            cache.set(key, pending)
        }
        return pending
    }
    return async (namespace: string, flowId: string): Promise<boolean> =>
        (await idsForNamespace(namespace)).has(flowId)
}

export async function filterExistingSubflowLinks(
    links: SubflowMonacoLink[],
    flowExists: (namespace: string, flowId: string) => Promise<boolean>,
): Promise<SubflowMonacoLink[]> {
    const exists = await Promise.all(
        links.map((link) => flowExists(link.target.namespace, link.target.flowId)),
    )
    return links.filter((_, index) => exists[index])
}
