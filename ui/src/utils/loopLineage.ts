import type {KsBreadcrumbItem} from "@kestra-io/design-system"
import {loadTaskRunOutputs} from "../composables/useTaskRunOutputs"
import {loopIterationsRoute} from "./executionUtils"
import {handled} from "./kestraHttp"
import {loadIterationExecution, type IterationParent} from "./loopIterations"
import {iterationNumber} from "./loopScope"

export const MAX_LINEAGE_DEPTH = 8
const SHORT_ID_LENGTH = 8
const MAX_VALUE_LENGTH = 24

export type LineageExecution = IterationParent & {id: string}

export interface LoopLineageLevel {
    executionId: string;
    parentExecutionId: string;
    taskId: string;
    number: number;
    count?: number;
    value?: string;
}

export interface LoopLineageDeps {
    fetchExecution: (executionId: string) => Promise<LineageExecution | undefined>;
    fetchIterationCount: (executionId: string, taskRunId: string) => Promise<number | undefined>;
}

export interface LoopLineageContext {
    namespace: string;
    flowId: string;
    t: (key: string, named?: Record<string, unknown>) => string;
}

export interface IterationsListFilters {
    parentId: string;
    taskId?: string;
}

async function loadIterationCount(executionId: string, taskRunId: string): Promise<number | undefined> {
    try {
        const outputs = await loadTaskRunOutputs(executionId, taskRunId)
        return typeof outputs.iterationCount === "number" ? outputs.iterationCount : undefined
    } catch (error) {
        handled(error)
        return undefined
    }
}

const defaultDeps: LoopLineageDeps = {
    fetchExecution: loadIterationExecution,
    fetchIterationCount: loadIterationCount,
}

const orUndefined = <T>(promise: Promise<T>) => promise.catch(() => undefined)

/** Levels from the outermost loop iteration down to `execution`; a parent that cannot be read ends the walk without failing. */
export async function resolveLoopLineage(
    execution: LineageExecution,
    deps: LoopLineageDeps = defaultDeps,
    maxDepth = MAX_LINEAGE_DEPTH,
): Promise<LoopLineageLevel[]> {
    const levels: LoopLineageLevel[] = []
    let current: LineageExecution | undefined = execution

    while (current && levels.length < maxDepth) {
        const {loopRun, parentId}: LineageExecution = current
        if (!loopRun?.taskId || !parentId) break

        const [parent, count]: [LineageExecution | undefined, number | undefined] = await Promise.all([
            orUndefined(deps.fetchExecution(parentId)),
            loopRun.taskRunId ? orUndefined(deps.fetchIterationCount(parentId, loopRun.taskRunId)) : undefined,
        ])
        levels.unshift({
            executionId: current.id,
            parentExecutionId: parentId,
            taskId: loopRun.taskId,
            number: iterationNumber(loopRun.index ?? 0),
            count,
            value: loopRun.value,
        })
        current = parent
    }

    return levels
}

export function iterationsListFilters(query: Record<string, unknown>): IterationsListFilters | undefined {
    const first = (key: string) => {
        const value = query[key]
        const single = Array.isArray(value) ? value[0] : value
        return typeof single === "string" && single ? single : undefined
    }

    const parentId = first("filters[parentId][EQUALS]")
    if (!parentId || first("filters[kind][EQUALS]") !== "LOOP") return undefined
    return {parentId, taskId: first("filters[taskId][EQUALS]")}
}

const shortId = (executionId: string) => executionId.slice(0, SHORT_ID_LENGTH)

function iterationValue(level: LoopLineageLevel) {
    const value = level.value?.trim()
    if (!value) return {}
    return value.length > MAX_VALUE_LENGTH
        ? {value: `${value.slice(0, MAX_VALUE_LENGTH - 1)}…`, full: value}
        : {value}
}

function iterationLabel(level: LoopLineageLevel, t: LoopLineageContext["t"]) {
    const {value, full} = iterationValue(level)
    const named = {index: level.number, count: level.count, value}
    const variant = `${level.count === undefined ? "" : "-count"}${value === undefined ? "" : "-value"}`
    return {label: t(`loop-lineage.iteration${variant}`, named), full}
}

const runLink = ({namespace, flowId}: LoopLineageContext, executionId: string) => ({
    name: "executions/update",
    params: {namespace, flowId, id: executionId},
})

const taskCrumb = (level: LoopLineageLevel): KsBreadcrumbItem => ({
    label: level.taskId,
    link: loopIterationsRoute({id: level.parentExecutionId}, level.taskId),
})

function runCrumb(level: LoopLineageLevel, context: LoopLineageContext): KsBreadcrumbItem {
    const {label, full} = iterationLabel(level, context.t)
    return {
        label: `${shortId(level.executionId)} ${label}`,
        link: runLink(context, level.executionId),
        tooltip: full,
    }
}

/** The loop tasks and iterations above an execution, up to its own loop task, and the title naming the execution as an iteration. */
export function executionLineage(levels: LoopLineageLevel[], context: LoopLineageContext): {crumbs: KsBreadcrumbItem[]; title: string} | undefined {
    const current = levels.at(-1)
    if (!current) return undefined

    return {
        crumbs: [...levels.slice(0, -1).flatMap((level) => [taskCrumb(level), runCrumb(level, context)]), taskCrumb(current)],
        title: `${current.executionId} ${iterationLabel(current, context.t).label}`,
    }
}

/** What leads to the iterations list of `parentId`: the parent's own lineage, the parent run, then the loop task. */
export function iterationsListLineage(
    parentId: string,
    levels: LoopLineageLevel[],
    taskId: string | undefined,
    context: LoopLineageContext,
): KsBreadcrumbItem[] {
    const lineage = levels.length
        ? levels.flatMap((level) => [taskCrumb(level), runCrumb(level, context)])
        : [{label: shortId(parentId), link: runLink(context, parentId)}]
    return taskId ? [...lineage, {label: taskId}] : lineage
}
