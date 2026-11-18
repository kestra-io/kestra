import {computed, onBeforeUnmount, ref, watch, type ComputedRef} from "vue"
import {CLUSTER_PREFIX, State, stringUtils, throttle} from "@kestra-io/design-system"
import {isLoopTaskType, loopIterationCountsOf, taskRunStateCountsOf, type LoopLaneData} from "@kestra-io/topology"
import type {FlowGraph} from "@kestra-io/topology/vue-flow-utils"
import {useExecutionsStore, type Execution} from "../stores/executions"
import {loadTaskRunOutputs} from "./useTaskRunOutputs"
import {useLoopScope} from "./useLoopScope"
import {AUTO_REFRESH_MS} from "./useLoopMergedLogs"
import {
    findFailedIterationChain,
    findFirstFailedIteration,
    findIterationByNumber,
    loadIterationExecution,
    LoopIterationError,
    type LoopIteration,
} from "../utils/loopIterations"
import {iterationNumber, withScopedIteration, type LoopScopeEntry} from "../utils/loopScope"

interface LoopLaneNode {
    uid: string;
    taskId: string;
    parentUid?: string;
}

interface LaneOutputs {
    key: string;
    outputs: Record<string, unknown>;
    fetchedAt: number;
}

type ScopeFailure = "forbidden" | "ambiguous" | "unknown" | "not-located"

export const SCOPED_ITERATIONS_REFRESH_MS = 2_000

const settle = <T>(promise: Promise<T>) => promise.catch(() => undefined)

const FAILED_OUTPUTS = "failed"

const afterLastDot = (uid: string) => stringUtils.afterLastDot(uid) ?? uid

export function loopLaneNodes(graph: Pick<FlowGraph, "clusters"> | undefined): LoopLaneNode[] {
    const clusters = graph?.clusters ?? []
    const subflowPaths = clusters
        .filter((entry) => entry.cluster.type.endsWith("SubflowGraphCluster"))
        .map((entry) => entry.cluster.uid.replace(CLUSTER_PREFIX, ""))
    const uids = clusters
        .map((entry) => entry.cluster.taskNode)
        .filter((taskNode) => taskNode && isLoopTaskType(taskNode.task.type))
        .map((taskNode) => taskNode!.uid)
        .filter((uid) => !subflowPaths.some((subflowPath) => uid.startsWith(`${subflowPath}.`)))

    return uids.map((uid) => ({
        uid,
        taskId: afterLastDot(uid),
        parentUid: uids.filter((other) => uid.startsWith(`${other}.`)).sort((a, b) => b.length - a.length)[0],
    }))
}

export function useLoopScoping(flowGraph: ComputedRef<FlowGraph | undefined>, enabled: ComputedRef<boolean>) {
    const executionsStore = useExecutionsStore()
    const {entries, key: scopeKey, setEntries} = useLoopScope()

    const laneNodes = computed(() => (enabled.value ? loopLaneNodes(flowGraph.value) : []))
    const laneNodesKey = computed(() => laneNodes.value.map((lane) => lane.uid).join("|"))
    const outputsByLane = ref<Record<string, LaneOutputs>>({})
    const scopeFailure = ref<ScopeFailure>()
    let userScopeSeq = 0
    let unmounted = false
    let ownsScopedIterations = false

    const executionOf = (laneUid: string): Execution | undefined => executionsStore.scopedIterations[laneUid]

    const hostOf = (lane: LoopLaneNode): Execution | undefined =>
        lane.parentUid ? executionOf(lane.parentUid) : executionsStore.execution

    const taskRunsIn = (host: Execution | undefined, taskId: string) => host?.taskRunList?.filter((run) => run.taskId === taskId) ?? []

    const singleTaskRunIn = (host: Execution | undefined, taskId: string) => {
        const runs = taskRunsIn(host, taskId)
        return runs.length === 1 ? runs[0] : undefined
    }

    const taskRunOf = (lane: LoopLaneNode) => singleTaskRunIn(hostOf(lane), lane.taskId)

    const hasSeveralTaskRuns = (lane: LoopLaneNode) => taskRunsIn(hostOf(lane), lane.taskId).length > 1

    const scopedNumberOf = (lane: LoopLaneNode) => {
        const index = loopDepth(lane)
        const entry = entries.value[index]
        const loaded = executionOf(lane.uid)
        const matches = entry?.taskId === lane.taskId && loaded?.loopRun?.index !== undefined && iterationNumber(loaded.loopRun.index) === entry.number
        return matches ? entry.number : undefined
    }

    function loopDepth(lane: LoopLaneNode): number {
        let depth = 0
        for (let parent = lane.parentUid; parent; parent = laneNodes.value.find((l) => l.uid === parent)?.parentUid) depth++
        return depth
    }

    const lanes = computed<Record<string, LoopLaneData>>(() => {
        const result: Record<string, LoopLaneData> = {}
        for (const lane of laneNodes.value) {
            const parent = laneNodes.value.find((other) => other.uid === lane.parentUid)
            const common = {
                taskId: lane.taskId,
                parentLaneUid: parent?.uid,
                parentTaskId: parent?.taskId,
                parentScoped: !parent || Boolean(executionOf(parent.uid)),
                scopedNumber: scopedNumberOf(lane),
                scopedValue: scopedNumberOf(lane) === undefined ? undefined : executionOf(lane.uid)?.loopRun?.value,
            }
            const host = hostOf(lane)
            const taskRun = taskRunOf(lane)

            if (!host) {
                const ancestorCounts = ancestorsOf(lane)
                result[lane.uid] = {
                    ...common,
                    status: "nested",
                    loopIterationCounts: ancestorCounts.loopIterationCounts,
                    taskRunStateCounts: ancestorCounts.taskRunStateCounts,
                }
                continue
            }
            if (!taskRun) {
                result[lane.uid] = {...common, status: hasSeveralTaskRuns(lane) ? "unknown" : "not-started"}
                continue
            }
            const fetched = outputsByLane.value[lane.uid]
            if (!fetched || fetched.key.split(":")[0] !== taskRun.id) {
                result[lane.uid] = {...common, status: "loading", state: taskRun.state.current}
                continue
            }
            const outputs = fetched.outputs
            result[lane.uid] = {
                ...common,
                status: typeof outputs.iterationCount === "number" ? "ready" : "unknown",
                iterationCount: outputs.iterationCount as number | undefined,
                runningIterations: outputs.runningIterations as number | undefined,
                terminatedIterations: outputs.terminatedIterations as Record<string, number> | undefined,
                state: taskRun.state.current,
                taskRunStateCounts: taskRunStateCountsOf(outputs),
                loopIterationCounts: loopIterationCountsOf(outputs),
            }
        }
        return result
    })

    function ancestorsOf(lane: LoopLaneNode) {
        const chain: LoopLaneNode[] = []
        for (let parent = laneNodes.value.find((l) => l.uid === lane.parentUid); parent; parent = laneNodes.value.find((l) => l.uid === parent!.parentUid)) {
            chain.push(parent)
        }
        const outputs = chain.map((ancestor) => outputsByLane.value[ancestor.uid]?.outputs)
        return {
            loopIterationCounts: outputs.map(loopIterationCountsOf).find(Boolean),
            taskRunStateCounts: outputs.map(taskRunStateCountsOf).find(Boolean),
        }
    }

    let outputsSeq = 0
    let outputsTimer: ReturnType<typeof setTimeout> | undefined
    const outputsInFlight = new Map<string, Promise<Record<string, unknown> | undefined>>()

    function fetchOutputs(hostId: string, taskRunId: string) {
        const key = `${hostId}:${taskRunId}`
        if (!outputsInFlight.has(key)) {
            outputsInFlight.set(key, settle(loadTaskRunOutputs(hostId, taskRunId)).finally(() => outputsInFlight.delete(key)))
        }
        return outputsInFlight.get(key)!
    }

    async function refreshOutputs() {
        const seq = ++outputsSeq
        const now = Date.now()
        let hasRunningLane = false
        const pending = laneNodes.value.flatMap((lane) => {
            const host = hostOf(lane)
            const taskRun = taskRunOf(lane)
            if (!host?.id || !taskRun) return []
            const cacheKey = `${taskRun.id}:${taskRun.state.current}:${host.id}`
            const cached = outputsByLane.value[lane.uid]
            const terminal = State.isTerminated(taskRun.state.current)
            hasRunningLane ||= !terminal
            if (cached?.key === cacheKey && (terminal || now - cached.fetchedAt < AUTO_REFRESH_MS)) return []
            return [{uid: lane.uid, cacheKey, hostId: host.id, taskRunId: taskRun.id}]
        })

        const loaded = await Promise.all(pending.map(async (item) => {
            const outputs = await fetchOutputs(item.hostId, item.taskRunId)
            return outputs ? {...item, outputs} : {...item, cacheKey: `${item.cacheKey}:${FAILED_OUTPUTS}`, outputs: {}}
        }))
        if (seq !== outputsSeq || unmounted) return

        const next = {...outputsByLane.value}
        loaded.forEach((item) => (next[item.uid] = {key: item.cacheKey, outputs: item.outputs, fetchedAt: now}))
        outputsByLane.value = next

        if (hasRunningLane && outputsTimer === undefined) {
            outputsTimer = setTimeout(() => {
                outputsTimer = undefined
                refreshOutputs()
            }, AUTO_REFRESH_MS)
        }
    }

    const throttledRefreshOutputs = throttle(refreshOutputs, 1000)

    watch(
        [() => executionsStore.execution, () => executionsStore.scopedIterations, laneNodesKey],
        () => throttledRefreshOutputs(),
        {immediate: true},
    )

    const isRunning = (execution: Execution) => Boolean(execution.state?.current && !State.isTerminated(execution.state.current))

    let scopedRefresh: Promise<void> | undefined
    let scopedTimer: ReturnType<typeof setTimeout> | undefined

    async function refreshScopedIterations() {
        const running = Object.entries(executionsStore.scopedIterations).filter(([, execution]) => execution.id && isRunning(execution))
        if (!running.length) return

        const refreshed = await Promise.all(running.map(async ([laneUid, execution]) => ({
            laneUid,
            executionId: execution.id!,
            updated: await settle(loadIterationExecution(execution.id!)),
        })))
        if (unmounted) return

        const next = {...executionsStore.scopedIterations}
        let changed = false
        for (const {laneUid, executionId, updated} of refreshed) {
            if (updated && next[laneUid]?.id === executionId) {
                next[laneUid] = updated
                changed = true
            }
        }
        if (changed) executionsStore.setScopedIterations(next)
    }

    function scheduleScopedRefresh() {
        const hasRunning = ownsScopedIterations && !unmounted && Object.values(executionsStore.scopedIterations).some(isRunning)
        if (!hasRunning) {
            clearTimeout(scopedTimer)
            scopedTimer = undefined
            return
        }
        if (scopedTimer !== undefined) return
        scopedTimer = setTimeout(async () => {
            scopedTimer = undefined
            scopedRefresh ??= refreshScopedIterations().finally(() => (scopedRefresh = undefined))
            await scopedRefresh
            scheduleScopedRefresh()
        }, SCOPED_ITERATIONS_REFRESH_MS)
    }

    watch(() => executionsStore.scopedIterations, scheduleScopedRefresh)

    let resolveSeq = 0
    async function resolveScope() {
        const seq = ++resolveSeq
        if (!laneNodes.value.length || !executionsStore.execution?.id) return

        const resolved: {lane: LoopLaneNode; iteration: LoopIteration; execution: Execution}[] = []
        let host: Execution = executionsStore.execution
        let parentLane: LoopLaneNode | undefined
        let transientFailure = false
        let forbidden = false
        let ambiguous = false

        const definitive = async <T>(promise: Promise<T>): Promise<T | undefined> => {
            try {
                return await promise
            } catch (error) {
                if (error instanceof LoopIterationError && error.failure === "forbidden") forbidden = true
                else if (error instanceof LoopIterationError && error.failure === "ambiguous") ambiguous = true
                else if (!(error instanceof LoopIterationError) || error.failure !== "not-found") transientFailure = true
                return undefined
            }
        }

        for (const entry of entries.value) {
            const lane = laneNodes.value.find((candidate) => candidate.taskId === entry.taskId && candidate.parentUid === parentLane?.uid)
            if (!lane) break
            const iteration = await definitive(findIterationByNumber(host.id!, lane.taskId, entry.number, singleTaskRunIn(host, lane.taskId)?.id))
            if (seq !== resolveSeq || unmounted) return
            if (!iteration) break
            const execution = await definitive(loadIterationExecution(iteration.id))
            if (seq !== resolveSeq || unmounted) return
            if (!execution) break
            resolved.push({lane, iteration, execution})
            host = execution
            parentLane = lane
        }

        if (transientFailure) scopeFailure.value = "unknown"
        else if (forbidden) scopeFailure.value = "forbidden"
        else if (ambiguous) scopeFailure.value = "ambiguous"
        else if (entries.value.length > 0) scopeFailure.value = undefined
        if (transientFailure) return

        ownsScopedIterations = true
        executionsStore.setScopedIterations(Object.fromEntries(resolved.map(({lane, execution}) => [lane.uid, execution])))
        refreshOutputs()

        if (resolved.length !== entries.value.length) {
            setEntries(resolved.map(({lane, iteration}) => ({taskId: lane.taskId, number: iteration.number})))
        }
    }

    watch([scopeKey, laneNodesKey, () => executionsStore.execution?.id], () => resolveScope(), {immediate: true})

    onBeforeUnmount(() => {
        unmounted = true
        clearTimeout(outputsTimer)
        throttledRefreshOutputs.cancel()
        clearTimeout(scopedTimer)
        if (ownsScopedIterations) executionsStore.setScopedIterations({})
    })

    const laneByUid = (uid: string) => laneNodes.value.find((lane) => lane.uid === uid)

    function scopeLane(laneUid: string, number: number) {
        const lane = laneByUid(laneUid)
        userScopeSeq++
        scopeFailure.value = undefined
        if (!lane) return
        setEntries(withScopedIteration(entries.value, loopDepth(lane), lane.taskId, number))
    }

    function clearLane(laneUid: string) {
        const lane = laneByUid(laneUid)
        userScopeSeq++
        scopeFailure.value = undefined
        if (lane) setEntries(entries.value.slice(0, loopDepth(lane)))
    }

    function clearScope() {
        userScopeSeq++
        scopeFailure.value = undefined
        setEntries([])
    }

    function stepLane(laneUid: string, delta: number) {
        const lane = laneByUid(laneUid)
        const data = lanes.value[laneUid]
        if (!lane || data?.status !== "ready" || !data.parentScoped) return
        const started = Math.min(data.iterationCount ?? 0, (data.runningIterations ?? 0) + Object.values(data.terminatedIterations ?? {}).reduce((a, b) => a + b, 0))
        if (started < 1) return
        const requested = entries.value[loopDepth(lane)]
        const current = (requested?.taskId === lane.taskId ? requested.number : undefined) ?? (delta > 0 ? 0 : started + 1)
        scopeLane(laneUid, Math.min(started, Math.max(1, current + delta)))
    }

    async function scopeFirstFailure(laneUid: string) {
        const seq = ++userScopeSeq
        scopeFailure.value = undefined
        const lane = laneByUid(laneUid)
        const root = executionsStore.execution
        if (!lane || !root?.id) return

        const host = hostOf(lane)
        const taskRun = taskRunOf(lane)
        if (!taskRun && hasSeveralTaskRuns(lane)) {
            scopeFailure.value = "ambiguous"
            return
        }

        const childOf = (parent: LoopLaneNode) => laneNodes.value.find((child) => child.parentUid === parent.uid)
        const failedInScope = host?.id && taskRun ? await settle(findFirstFailedIteration(host.id, lane.taskId, taskRun.id)) : undefined
        if (seq !== userScopeSeq) return

        let chain: LoopScopeEntry[]
        let parentId: string
        if (failedInScope) {
            chain = [...entries.value.slice(0, loopDepth(lane)), {taskId: lane.taskId, number: failedInScope.number}]
            parentId = failedInScope.id
        } else {
            const found = await settle(findFailedIterationChain(
                {id: root.id, namespace: root.namespace, flowId: root.flowId, startDate: root.state?.startDate},
                lane.taskId,
            ))
            if (seq !== userScopeSeq) return
            if (!found) {
                scopeFailure.value = "not-located"
                return
            }
            chain = found.entries
            parentId = found.leafId
        }
        let current = childOf(lane)

        while (current) {
            const parent = await settle(loadIterationExecution(parentId))
            const childTaskRuns = taskRunsIn(parent, current.taskId)
            if (seq !== userScopeSeq) return
            if (childTaskRuns.length !== 1) break
            const failed = await settle(findFirstFailedIteration(parentId, current.taskId, childTaskRuns[0].id))
            if (seq !== userScopeSeq) return
            if (!failed) break
            chain.push({taskId: current.taskId, number: failed.number})
            parentId = failed.id
            current = childOf(current)
        }
        setEntries(chain)
    }

    function firstFailedLaneUid(): string | undefined {
        for (const lane of laneNodes.value) {
            const data = lanes.value[lane.uid]
            if (data?.status !== "ready") continue
            if ((data.terminatedIterations?.FAILED ?? 0) > 0) return lane.uid
            const child = laneNodes.value.find((other) =>
                other.parentUid === lane.uid && (data.loopIterationCounts?.[other.taskId]?.FAILED ?? 0) > 0,
            )
            if (child) return child.uid
        }
        return undefined
    }

    const scopeTrail = computed(() =>
        entries.value.map((entry, depth) => {
            const lane = laneNodes.value.find((candidate) => candidate.taskId === entry.taskId && loopDepth(candidate) === depth)
            return {...entry, value: lane ? lanes.value[lane.uid]?.scopedValue : undefined}
        }),
    )

    function hostExecutionId(laneUid: string): string | undefined {
        const lane = laneByUid(laneUid)
        return lane && !hasSeveralTaskRuns(lane) ? hostOf(lane)?.id : undefined
    }

    const lanesWithoutFailures = computed(() =>
        laneNodes.value.filter((lane) => {
            const data = lanes.value[lane.uid]
            return data?.status === "ready" && (data.terminatedIterations?.FAILED ?? 0) === 0
        }).map((lane) => lane.uid),
    )

    return {
        lanes,
        laneNodes,
        entries,
        scopeTrail,
        scopeFailure,
        retryScope: resolveScope,
        lanesWithoutFailures,
        scopeLane,
        clearLane,
        clearScope,
        stepLane,
        scopeFirstFailure,
        firstFailedLaneUid,
        hostExecutionId,
    }
}
