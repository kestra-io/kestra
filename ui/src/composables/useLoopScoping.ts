import {computed, onBeforeUnmount, ref, watch, type ComputedRef} from "vue"
import {stringUtils, throttle} from "@kestra-io/design-system"
import {isLoopTaskType, loopIterationCountsOf, taskRunStateCountsOf, type LoopLaneData} from "@kestra-io/topology"
import type {FlowGraph} from "@kestra-io/topology/vue-flow-utils"
import {useExecutionsStore, type Execution} from "../stores/executions"
import {loadTaskRunOutputs} from "./useTaskRunOutputs"
import {useLoopScope} from "./useLoopScope"
import {
    findFailedIterationChain,
    findFirstFailedIteration,
    findIterationByNumber,
    loadIterationExecution,
    LoopIterationError,
    type LoopIteration,
} from "../utils/loopIterations"
import {withScopedIteration, type LoopScopeEntry} from "../utils/loopScope"

interface LoopLaneNode {
    uid: string;
    taskId: string;
    parentUid?: string;
}

interface LaneOutputs {
    key: string;
    outputs: Record<string, unknown>;
}

const settle = <T>(promise: Promise<T>) => promise.catch(() => undefined)

const afterLastDot = (uid: string) => stringUtils.afterLastDot(uid) ?? uid

const RUNNING_STATES = ["CREATED", "QUEUED", "RUNNING", "RESTARTED", "KILLING", "PAUSED", "RETRYING"]
const TERMINAL_STATES = ["SUCCESS", "FAILED", "KILLED", "WARNING", "CANCELLED"]

interface FollowedExecution {
    executionId: string;
    close: () => void;
}

export function loopLaneNodes(graph: Pick<FlowGraph, "clusters"> | undefined): LoopLaneNode[] {
    const uids = (graph?.clusters ?? [])
        .map((entry) => entry.cluster.taskNode)
        .filter((taskNode) => taskNode && isLoopTaskType(taskNode.task.type))
        .map((taskNode) => taskNode!.uid)

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
    const scopeFailure = ref(false)
    const subscriptions = new Map<string, FollowedExecution>()
    let userScopeSeq = 0
    let unmounted = false

    const executionOf = (laneUid: string): Execution | undefined => executionsStore.subflowsExecutions[laneUid]

    const hostOf = (lane: LoopLaneNode): Execution | undefined =>
        lane.parentUid ? executionOf(lane.parentUid) : executionsStore.execution

    const taskRunOf = (lane: LoopLaneNode) => hostOf(lane)?.taskRunList?.find((run) => run.taskId === lane.taskId)

    const scopedNumberOf = (lane: LoopLaneNode) => {
        const index = loopDepth(lane)
        const entry = entries.value[index]
        return entry?.taskId === lane.taskId && executionOf(lane.uid) ? entry.number : undefined
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
                result[lane.uid] = {...common, status: "not-started"}
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
    async function refreshOutputs() {
        const seq = ++outputsSeq
        const pending = laneNodes.value.flatMap((lane) => {
            const host = hostOf(lane)
            const taskRun = taskRunOf(lane)
            if (!host?.id || !taskRun) return []
            const cacheKey = `${taskRun.id}:${taskRun.state.current}:${host.id}`
            const cached = outputsByLane.value[lane.uid]
            const terminal = TERMINAL_STATES.includes(taskRun.state.current)
            if (cached?.key === cacheKey && terminal) return []
            return [{uid: lane.uid, cacheKey, hostId: host.id, taskRunId: taskRun.id}]
        })

        const loaded = await Promise.all(pending.map(async (item) => {
            const outputs = await settle(loadTaskRunOutputs(item.hostId, item.taskRunId))
            return outputs ? {...item, outputs} : undefined
        }))
        if (seq !== outputsSeq || unmounted) return

        const next = {...outputsByLane.value}
        loaded.forEach((item) => item && (next[item.uid] = {key: item.cacheKey, outputs: item.outputs}))
        outputsByLane.value = next
    }

    const throttledRefreshOutputs = throttle(refreshOutputs, 1000)

    watch(
        [() => executionsStore.execution, () => executionsStore.subflowsExecutions, laneNodesKey],
        () => throttledRefreshOutputs(),
        {immediate: true},
    )

    let resolveSeq = 0
    async function resolveScope() {
        const seq = ++resolveSeq
        if (!laneNodes.value.length || !executionsStore.execution?.id) return

        const resolved: {lane: LoopLaneNode; iteration: LoopIteration; execution: Execution}[] = []
        let parentId = executionsStore.execution.id
        let parentLane: LoopLaneNode | undefined
        let transientFailure = false

        const definitive = async <T>(promise: Promise<T>): Promise<T | undefined> => {
            try {
                return await promise
            } catch (error) {
                if (!(error instanceof LoopIterationError) || error.failure !== "not-found") transientFailure = true
                return undefined
            }
        }

        for (const entry of entries.value) {
            const lane = laneNodes.value.find((candidate) => candidate.taskId === entry.taskId && candidate.parentUid === parentLane?.uid)
            if (!lane) break
            const iteration = await definitive(findIterationByNumber(parentId, lane.taskId, entry.number))
            if (seq !== resolveSeq) return
            if (!iteration) break
            const execution = await definitive(loadIterationExecution(iteration.id))
            if (seq !== resolveSeq) return
            if (!execution) break
            resolved.push({lane, iteration, execution})
            parentId = iteration.id
            parentLane = lane
        }

        scopeFailure.value = transientFailure
        if (transientFailure) return

        const keep = new Set(resolved.map(({lane}) => lane.uid))
        laneNodes.value.filter((lane) => !keep.has(lane.uid)).forEach((lane) => {
            if (executionsStore.subflowsExecutions[lane.uid]) executionsStore.removeSubflowExecution(lane.uid)
            closeSubscription(lane.uid)
        })
        resolved.forEach(({lane, execution}) => {
            executionsStore.addSubflowExecution({subflow: lane.uid, execution})
            follow(lane.uid, execution)
        })
        executionsStore.applyScopedExecutionIds()
        refreshOutputs()

        if (resolved.length !== entries.value.length) {
            setEntries(resolved.map(({lane, iteration}) => ({taskId: lane.taskId, number: iteration.number})))
        }
    }

    function closeSubscription(laneUid: string) {
        subscriptions.get(laneUid)?.close()
        subscriptions.delete(laneUid)
    }

    function follow(laneUid: string, execution: Execution) {
        const current = subscriptions.get(laneUid)
        if (current?.executionId === execution.id) return
        closeSubscription(laneUid)
        if (!execution.state?.current || !RUNNING_STATES.includes(execution.state.current) || unmounted) return

        const subscription = executionsStore.subscribeToExecution(execution.id, {
            onExecution: (updated) => {
                if (executionsStore.subflowsExecutions[laneUid]?.id !== execution.id) return
                executionsStore.addSubflowExecution({subflow: laneUid, execution: updated})
            },
            onEnd: () => {
                if (subscriptions.get(laneUid)?.executionId === execution.id) closeSubscription(laneUid)
            },
        })
        subscriptions.set(laneUid, {executionId: execution.id, close: subscription.close})
    }

    watch([scopeKey, laneNodesKey, () => executionsStore.execution?.id], () => resolveScope(), {immediate: true})

    onBeforeUnmount(() => {
        unmounted = true
        subscriptions.forEach((subscription) => subscription.close())
        subscriptions.clear()
        laneNodes.value.forEach((lane) => {
            if (executionsStore.subflowsExecutions[lane.uid]) executionsStore.removeSubflowExecution(lane.uid)
        })
    })

    const laneByUid = (uid: string) => laneNodes.value.find((lane) => lane.uid === uid)

    function scopeLane(laneUid: string, number: number) {
        const lane = laneByUid(laneUid)
        userScopeSeq++
        if (!lane) return
        setEntries(withScopedIteration(entries.value, loopDepth(lane), lane.taskId, number))
    }

    function clearLane(laneUid: string) {
        const lane = laneByUid(laneUid)
        userScopeSeq++
        if (lane) setEntries(entries.value.slice(0, loopDepth(lane)))
    }

    function clearScope() {
        userScopeSeq++
        setEntries([])
    }

    function stepLane(laneUid: string, delta: number) {
        const lane = laneByUid(laneUid)
        const data = lanes.value[laneUid]
        if (!lane || data?.status !== "ready" || !data.parentScoped) return
        const started = Math.min(data.iterationCount ?? 0, (data.runningIterations ?? 0) + Object.values(data.terminatedIterations ?? {}).reduce((a, b) => a + b, 0))
        if (started < 1) return
        const current = data.scopedNumber ?? (delta > 0 ? 0 : started + 1)
        scopeLane(laneUid, Math.min(started, Math.max(1, current + delta)))
    }

    async function scopeFirstFailure(laneUid: string) {
        const seq = ++userScopeSeq
        const lane = laneByUid(laneUid)
        const root = executionsStore.execution
        if (!lane || !root?.id) return

        const host = hostOf(lane)
        let chain: LoopScopeEntry[]
        let parentId: string
        let current: LoopLaneNode | undefined

        if (host?.id) {
            chain = entries.value.slice(0, loopDepth(lane))
            parentId = host.id
            current = lane
        } else {
            const found = await settle(findFailedIterationChain(
                {id: root.id, namespace: root.namespace, flowId: root.flowId, startDate: root.state?.startDate},
                lane.taskId,
            ))
            if (seq !== userScopeSeq || !found) return
            chain = found.entries
            parentId = found.leafId
            current = laneNodes.value.find((child) => child.parentUid === lane.uid)
        }

        while (current) {
            const failed = await settle(findFirstFailedIteration(parentId, current.taskId))
            if (seq !== userScopeSeq) return
            if (!failed) break
            chain.push({taskId: current.taskId, number: failed.number})
            parentId = failed.id
            const uid: string = current.uid
            current = laneNodes.value.find((child) => child.parentUid === uid)
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
        return lane ? hostOf(lane)?.id : undefined
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
