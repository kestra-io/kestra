import type {RouteLocationRaw} from "vue-router"
import {useExecutionsStore} from "../stores/executions"

export interface SubflowExecutionRef {
    id: string;
    namespace: string;
    flowId: string;
    flowRevision?: number;
    state: {current: string; histories: Array<{state: string; date: string}>};
    triggerExecutionId: string;
    taskRunId?: string;
}

const SEARCH_SIZE = 100
const MAX_PAGES = 10

function stringVariable(variables: unknown, key: string): string | undefined {
    if (variables === null || typeof variables !== "object") return undefined
    const value = (variables as Record<string, unknown>)[key]
    return typeof value === "string" ? value : undefined
}

function toSubflowExecutionRef(execution: {
    id: string;
    namespace: string;
    flowId: string;
    flowRevision?: number;
    state: {current?: string; histories?: Array<{state?: string; date?: string}>};
    trigger?: {variables?: unknown};
}): SubflowExecutionRef | undefined {
    const triggerExecutionId = stringVariable(execution.trigger?.variables, "executionId")
    if (!triggerExecutionId) return undefined
    return {
        id: execution.id,
        namespace: execution.namespace,
        flowId: execution.flowId,
        flowRevision: execution.flowRevision,
        state: {
            current: execution.state.current ?? "",
            histories: (execution.state.histories ?? []).map(history => ({state: history.state ?? "", date: history.date ?? ""})),
        },
        triggerExecutionId,
        taskRunId: stringVariable(execution.trigger?.variables, "taskRunId"),
    }
}

type KindFilter = Record<string, string | string[]>

/**
 * A subflow execution takes the kind of the run it belongs to. A search without a kind only returns NORMAL ones, which
 * no kind filter matches since their kind is stored empty, and a Loop iteration does not say which kind its run has.
 */
function kindFilters(parentKind: string | null | undefined): KindFilter[] {
    if (parentKind === "PLAYGROUND" || parentKind === "TEST") return [{"filters[kind][EQUALS]": parentKind}]
    if (parentKind === "LOOP") return [{}, {"filters[kind][IN]": ["PLAYGROUND", "TEST"]}]
    return [{}]
}

async function searchAllPages(triggerExecutionIds: string[], kindFilter: KindFilter): Promise<SubflowExecutionRef[]> {
    const found: SubflowExecutionRef[] = []
    for (let page = 1; page <= MAX_PAGES; page++) {
        const response = await useExecutionsStore().findExecutions({
            "filters[triggerExecutionId][IN]": triggerExecutionIds,
            ...kindFilter,
            page,
            size: SEARCH_SIZE,
            sort: "state.startDate:asc",
            commit: false,
        })
        const results = response.results ?? []
        found.push(...results.flatMap(execution => toSubflowExecutionRef(execution) ?? []))
        if (results.length < SEARCH_SIZE || page * SEARCH_SIZE >= (response.total ?? 0)) break
    }
    return found
}

/**
 * Executions started by a Subflow task of the given executions, found through their trigger: a subflow started inside
 * a Loop iteration has no `parentId`, only `trigger.variables.executionId` pointing at the iteration.
 *
 * @param parentKind the kind of the executions searched from, which decides the kind of what they started
 */
export async function findSubflowExecutions(triggerExecutionIds: string[], parentKind?: string | null): Promise<SubflowExecutionRef[]> {
    if (triggerExecutionIds.length === 0) return []
    const pages = await Promise.all(kindFilters(parentKind).map(filter => searchAllPages(triggerExecutionIds, filter)))
    return pages.flat()
}

export function subflowExecutionRoute(execution: Pick<SubflowExecutionRef, "id" | "namespace" | "flowId">): RouteLocationRaw {
    return {
        name: "executions/update",
        params: {namespace: execution.namespace, flowId: execution.flowId, id: execution.id},
    }
}
