import {State} from "@kestra-io/design-system"

export const LOOP_TYPE_SUFFIX = ".flow.Loop"

export const TASK_RUN_STATE_COUNTS_KEY = "taskRunStateCounts"

export const LOOP_ITERATION_COUNTS_KEY = "loopIterationCounts"

export type StateCounts = Record<string, number>
export type TaskRunStateCounts = Record<string, StateCounts>

export interface LoopLaneData {
    taskId: string;
    status: "loading" | "not-started" | "ready" | "nested" | "unknown";
    iterationCount?: number;
    runningIterations?: number;
    terminatedIterations?: StateCounts;
    state?: string;
    taskRunStateCounts?: TaskRunStateCounts;
    loopIterationCounts?: TaskRunStateCounts;
    scopedNumber?: number;
    parentLaneUid?: string;
    parentTaskId?: string;
    parentScoped: boolean;
}

export interface LoopOutcome {
    total: number;
    done: number;
    running: number;
    failed: number;
    notStarted: number;
    finished: boolean;
    state?: string;
}

export type LoopChipKind = "failed" | "failed-not-started" | "progress" | "success"

export interface LoopChip {
    kind: LoopChipKind;
    total: number;
    done: number;
    failed: number;
    notStarted: number;
}

export interface LoopTaskRunSummary {
    runs: number;
    failed: number;
}

const FAILED_STATE = "FAILED"

export const isLoopTaskType = (type?: string): boolean => Boolean(type?.endsWith(LOOP_TYPE_SUFFIX))

function countsAt(outputs: Record<string, unknown> | undefined, key: string): TaskRunStateCounts | undefined {
    const raw = outputs?.[key]
    return raw && typeof raw === "object" && !Array.isArray(raw) ? raw as TaskRunStateCounts : undefined
}

export const taskRunStateCountsOf = (outputs?: Record<string, unknown>) => countsAt(outputs, TASK_RUN_STATE_COUNTS_KEY)

export const loopIterationCountsOf = (outputs?: Record<string, unknown>) => countsAt(outputs, LOOP_ITERATION_COUNTS_KEY)

export function summarizeTaskRuns(counts?: StateCounts): LoopTaskRunSummary | undefined {
    if (!counts) return undefined
    const runs = Object.values(counts).reduce((sum, count) => sum + count, 0)
    return runs > 0 ? {runs, failed: counts[FAILED_STATE] ?? 0} : undefined
}

export function loopOutcome(data: Pick<LoopLaneData, "iterationCount" | "runningIterations" | "terminatedIterations" | "state">): LoopOutcome | undefined {
    const total = data.iterationCount ?? 0
    if (total <= 0) return undefined

    const running = data.runningIterations ?? 0
    const terminated = data.terminatedIterations ?? {}
    const done = Object.values(terminated).reduce((sum, count) => sum + count, 0)
    const failed = terminated[FAILED_STATE] ?? 0
    const finished = data.state !== undefined && State.isTerminated(data.state)

    return {
        total,
        done,
        running,
        failed,
        notStarted: Math.max(0, total - running - done),
        finished,
        state: stateNotSaidByChip(data.state, failed),
    }
}

function stateNotSaidByChip(state: string | undefined, failed: number): string | undefined {
    if (!state) return undefined
    if (state === "RUNNING") return undefined
    if (state === FAILED_STATE && failed > 0) return undefined
    if (state === "SUCCESS" && failed === 0) return undefined
    return state
}

export function loopChip(outcome: LoopOutcome): LoopChip {
    const {total, done, failed, notStarted, finished} = outcome
    const base = {total, done, failed, notStarted}

    if (!finished) {
        return {kind: "progress", ...base}
    }
    if (failed > 0) {
        return {kind: notStarted > 0 ? "failed-not-started" : "failed", ...base}
    }
    if (notStarted > 0) {
        return {kind: "progress", ...base}
    }
    return {kind: "success", ...base}
}

export function loopChipFromIterations(summary: LoopTaskRunSummary): LoopChip {
    return {
        kind: summary.failed > 0 ? "failed" : "success",
        total: summary.runs,
        done: summary.runs,
        failed: summary.failed,
        notStarted: 0,
    }
}

export function enclosingLoopUids(nodeUid: string, lanes: Record<string, LoopLaneData>): string[] {
    return Object.keys(lanes)
        .filter((laneUid) => nodeUid.startsWith(`${laneUid}.`))
        .sort((a, b) => a.length - b.length)
}

export interface LoopTaskContext {
    unscoped: boolean;
    summary?: LoopTaskRunSummary;
}

export function loopTaskContext(nodeUid: string, taskId: string, lanes: Record<string, LoopLaneData>): LoopTaskContext | undefined {
    const enclosing = enclosingLoopUids(nodeUid, lanes)
    if (!enclosing.length) return undefined

    const innermost = lanes[enclosing[enclosing.length - 1]]
    if (innermost.scopedNumber !== undefined) return {unscoped: false}

    const counts = [...enclosing].reverse()
        .map((laneUid) => lanes[laneUid].taskRunStateCounts)
        .find((candidate) => candidate !== undefined)

    return {unscoped: true, summary: summarizeTaskRuns(counts?.[taskId])}
}

export function nestedLoopIterationSummary(lane: LoopLaneData, laneUid: string, lanes: Record<string, LoopLaneData>): LoopTaskRunSummary | undefined {
    const counts = [...enclosingLoopUids(laneUid, lanes)].reverse()
        .map((enclosingUid) => lanes[enclosingUid].loopIterationCounts)
        .find((candidate) => candidate !== undefined)

    return summarizeTaskRuns(counts?.[lane.taskId])
}
