import {ref, watch} from "vue"
import type {RouteLocationRaw} from "vue-router"
import {useExecutionsStore} from "../stores/executions"
import {latestRequest} from "../utils/latestRequest"

export interface SubflowExecutionRef {
    id: string;
    namespace: string;
    flowId: string;
    state: string;
    triggerExecutionId: string;
    taskRunId?: string;
}

const SEARCH_SIZE = 100
const CORRELATION_ID_LABEL = "system.correlationId"

function stringVariable(variables: unknown, key: string): string | undefined {
    if (variables === null || typeof variables !== "object") return undefined
    const value = (variables as Record<string, unknown>)[key]
    return typeof value === "string" ? value : undefined
}

function toSubflowExecutionRef(execution: {
    id: string;
    namespace: string;
    flowId: string;
    state: {current?: string};
    trigger?: {variables?: unknown};
}): SubflowExecutionRef | undefined {
    const triggerExecutionId = stringVariable(execution.trigger?.variables, "executionId")
    if (!triggerExecutionId) return undefined
    return {
        id: execution.id,
        namespace: execution.namespace,
        flowId: execution.flowId,
        state: execution.state.current ?? "",
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

/**
 * Every execution a Subflow task started under the run of `execution`, whatever the depth: they share its correlation id,
 * and `childFilter` drops the run's own executions while the default NORMAL kind drops its Loop iterations.
 */
export function subflowExecutionsOfRunRoute(execution: {id: string; labels?: Array<{key?: string; value?: string}>}): RouteLocationRaw {
    const correlationId = execution.labels?.find(label => label.key === CORRELATION_ID_LABEL)?.value ?? execution.id
    return {
        name: "executions/list",
        query: {
            [`filters[labels][EQUALS][${CORRELATION_ID_LABEL}]`]: correlationId,
            "filters[childFilter][EQUALS]": "CHILD",
        },
    }
}

/** The subflow executions of each of `executions`, looked up again whenever one of them changes state. */
export function useSubflowExecutions(executions: () => Array<{id: string; state: {current: string}}>) {
    const byTriggerExecutionId = ref(new Map<string, SubflowExecutionRef[]>())
    const startFetch = latestRequest()

    async function load(ids: string[]) {
        const isLatest = startFetch()
        try {
            const found = await findSubflowExecutions(ids)
            if (!isLatest()) return
            const grouped = new Map<string, SubflowExecutionRef[]>()
            for (const execution of found) {
                grouped.set(execution.triggerExecutionId, [...(grouped.get(execution.triggerExecutionId) ?? []), execution])
            }
            byTriggerExecutionId.value = grouped
        } catch {
            // A failed lookup only hides the links; the iterations themselves stay usable.
            if (isLatest()) byTriggerExecutionId.value = new Map()
        }
    }

    watch(
        () => executions().map(execution => `${execution.id}:${execution.state.current}`).join(","),
        () => load(executions().map(execution => execution.id)),
        {immediate: true},
    )

    return {
        byTriggerExecutionId,
        subflowExecutionsOf: (executionId: string) => byTriggerExecutionId.value.get(executionId) ?? [],
    }
}
