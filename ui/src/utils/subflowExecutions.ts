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

/**
 * Executions started by a Subflow task of the given executions, found through their trigger: a subflow started inside
 * a Loop iteration has no `parentId`, only `trigger.variables.executionId` pointing at the iteration.
 */
export async function findSubflowExecutions(triggerExecutionIds: string[]): Promise<SubflowExecutionRef[]> {
    if (triggerExecutionIds.length === 0) return []
    const response = await useExecutionsStore().findExecutions({
        "filters[triggerExecutionId][IN]": triggerExecutionIds,
        page: 1,
        size: SEARCH_SIZE,
        commit: false,
    })
    return (response.results ?? []).flatMap(execution => toSubflowExecutionRef(execution) ?? [])
}

export function subflowExecutionRoute(execution: Pick<SubflowExecutionRef, "id" | "namespace" | "flowId">): RouteLocationRaw {
    return {
        name: "executions/update",
        params: {namespace: execution.namespace, flowId: execution.flowId, id: execution.id},
    }
}
