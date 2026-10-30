import {computed, inject, nextTick, provide, reactive, ref, watch, type ComputedRef, type InjectionKey, type Ref} from "vue"
import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
import {State, type LevelFilterValue} from "@kestra-io/design-system"
import {useExecutionsStore, type Execution} from "../../../stores/executions"
import * as FlowUtils from "../../../utils/flowUtils"
import {buildTaskRunHierarchy} from "../../../utils/taskRunHierarchy"
import {firstFailedLeaf, isFailedTaskRunState as isFailed, isHiddenTaskType} from "../../../utils/executionUtils"
import {useLoopIterations, type LoopIterationRow} from "../../../composables/useLoopIterations"
import {loadTaskRunOutputs} from "../../../composables/useTaskRunOutputs"
import type {ExecutionTaskRun} from "../executionTaskRun"

export const LOOP_TYPE = "io.kestra.plugin.core.flow.Loop"

export {firstFailedLeaf, isFailed, isHiddenTaskType}

export type TaskTypeResolver = (taskId: string) => string | undefined

export interface LoopRef {
    parentExecutionId: string;
    taskId: string;
    namespace: string;
    flowId: string;
}

export interface Ancestor {
    key: string;
    label: string;
    iteration?: IterationNode;
}

export interface TaskRunNode {
    kind: "taskRun" | "loop";
    key: string;
    executionId: string;
    namespace: string;
    flowId: string;
    taskRun: ExecutionTaskRun;
    taskType?: string;
    ancestors: Ancestor[];
}

export interface ExecutionRef {
    id: string;
    namespace: string;
    flowId: string;
}

export interface IterationNode {
    kind: "iteration";
    key: string;
    loop: LoopRef;
    row: LoopIterationRow;
    ancestors: Ancestor[];
}

export type GanttNode = TaskRunNode | IterationNode

export type LoopHandle = ReturnType<typeof useLoopIterations>

export interface ExecutionEntry {
    execution?: Execution;
    error?: unknown;
    loading: boolean;
}

export const LOOP_OUTPUTS_REFRESH_MS = 10_000

export interface LoopOutputsEntry {
    outputs?: Record<string, unknown>;
    fetchedAt: number;
    terminal: boolean;
    failed: boolean;
}

export interface GanttTree {
    selected: Ref<GanttNode | undefined>;
    panelOpen: Ref<boolean>;
    highlightedKey: ComputedRef<string | undefined>;
    failedOnly: Ref<boolean>;
    compact: Ref<boolean>;
    levelFilter: Ref<LevelFilterValue | undefined>;
    stepping: Ref<boolean>;
    select: (node: GanttNode, options?: {reveal?: boolean}) => void;
    closePanel: () => void;
    reset: () => void;
    refresh: () => Promise<void>;
    isExpanded: (key: string) => boolean;
    setExpanded: (key: string, value: boolean) => void;
    toggleExpanded: (key: string) => void;
    loopHandle: (loop: LoopRef) => LoopHandle;
    executionEntry: (executionId: string) => ExecutionEntry;
    loadExecution: (executionId: string, force?: boolean) => Promise<Execution | undefined>;
    loopOutputs: (taskRunId: string) => LoopOutputsEntry | undefined;
    loadLoopOutputs: (node: TaskRunNode) => Promise<void>;
    taskType: (taskId: string) => string | undefined;
    hasFailure: (node: TaskRunNode) => boolean;
    selectIteration: (loopNode: TaskRunNode, row: LoopIterationRow) => Promise<void>;
    stepIteration: (node: GanttNode, delta: -1 | 1) => Promise<void>;
    revealFirstFailure: (start: TaskRunNode) => Promise<void>;
}

export const GANTT_TREE: InjectionKey<GanttTree> = Symbol("ganttTree")

export function useGanttTree(): GanttTree {
    const tree = inject(GANTT_TREE)
    if (!tree) {
        throw new Error("The Gantt tree is not provided: render this component inside the Gantt.")
    }
    return tree
}

const MAX_REVEAL_FRAMES = 30
const SETTLE_FRAMES = 6

const nextFrame = () => new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
const rowElement = (key: string) => document.querySelector<HTMLElement>(`[data-row-key="${CSS.escape(key)}"]`)

const ts = (date: string): number => new Date(date).getTime()

export const taskRunKey = (taskRunId: string): string => `tr:${taskRunId}`
export const iterationKey = (executionId: string): string => `it:${executionId}`
export const iterationLabel = (row: LoopIterationRow): string => `#${row.index + 1} ${row.value}`

export function orderTaskRuns(taskRuns: ExecutionTaskRun[], taskType: TaskTypeResolver): Array<{task: ExecutionTaskRun; depth: number}> {
    return buildTaskRunHierarchy(
        taskRuns.filter(taskRun => (taskRun.state?.histories?.length ?? 0) > 0),
        (a, b) => ts(a.state.histories[0].date) - ts(b.state.histories[0].date),
    ).filter(({task}) => !isHiddenTaskType(taskType(task.taskId)))
}

export function loadedNeighbour(
    handle: Pick<LoopHandle, "iterations" | "loaded" | "hasMore">,
    current: LoopIterationRow,
    delta: -1 | 1,
): {known: boolean; row?: LoopIterationRow; position?: number} {
    if (!handle.loaded.value) return {known: false}
    const rows = handle.iterations.value
    const position = rows.findIndex(row => row.id === current.id)
    if (position >= 0) {
        const row = rows[position + delta]
        return row || delta === -1 || !handle.hasMore.value ? {known: true, row} : {known: false, position}
    }
    const after = rows.find(row => row.index > current.index)
    if (delta === 1) return after || !handle.hasMore.value ? {known: true, row: after} : {known: false}
    return after || !handle.hasMore.value
        ? {known: true, row: [...rows].reverse().find(row => row.index < current.index)}
        : {known: false}
}

export function loopRefOf(node: TaskRunNode): LoopRef {
    return {
        parentExecutionId: node.executionId,
        taskId: node.taskRun.taskId,
        namespace: node.namespace,
        flowId: node.flowId,
    }
}

export function loopAncestors(node: TaskRunNode): Ancestor[] {
    return [...node.ancestors, {key: node.key, label: node.taskRun.taskId}]
}

export function createIterationNode(loop: LoopRef, row: LoopIterationRow, ancestors: Ancestor[]): IterationNode {
    return {kind: "iteration", key: iterationKey(row.id), loop, row, ancestors}
}

export function iterationAncestors(node: IterationNode): Ancestor[] {
    return [...node.ancestors, {key: node.key, label: iterationLabel(node.row), iteration: node}]
}

export function createTaskRunNode(
    execution: ExecutionRef,
    taskRun: ExecutionTaskRun,
    taskType: string | undefined,
    ancestors: Ancestor[],
): TaskRunNode {
    return {
        kind: taskType === LOOP_TYPE ? "loop" : "taskRun",
        key: taskRunKey(taskRun.id),
        executionId: execution.id,
        namespace: execution.namespace,
        flowId: execution.flowId,
        taskRun,
        taskType,
        ancestors,
    }
}

export function indexTaskRuns(taskRuns: ExecutionTaskRun[]): Map<string, ExecutionTaskRun> {
    return new Map(taskRuns.map(taskRun => [taskRun.id, taskRun]))
}

export function taskRunAncestors(byId: Map<string, ExecutionTaskRun>, taskRun: ExecutionTaskRun, base: Ancestor[]): Ancestor[] {
    const chain: Ancestor[] = []
    let parent = byId.get(taskRun.parentTaskRunId ?? "")
    while (parent) {
        chain.unshift({key: taskRunKey(parent.id), label: parent.taskId})
        parent = byId.get(parent.parentTaskRunId ?? "")
    }
    return [...base, ...chain]
}

export function owningIteration(node: GanttNode): IterationNode | undefined {
    if (node.kind === "iteration") return node
    return [...node.ancestors].reverse().find(ancestor => ancestor.iteration)?.iteration
}

export function createGanttTree(options: {
    compact: Ref<boolean>;
    levelFilter: Ref<LevelFilterValue | undefined>;
    scrollToRow?: (node: GanttNode) => void;
}): GanttTree {
    const executionsStore = useExecutionsStore()
    const selected = ref<GanttNode | undefined>(undefined)
    const panelOpen = ref(false)
    const highlightedKey = computed<string | undefined>(() => {
        const node = selected.value
        if (!node) return undefined
        return node.kind === "taskRun" ? owningIteration(node)?.key : node.key
    })
    const failedOnly = ref(false)
    const stepping = ref(false)
    const expanded = reactive(new Set<string>())
    const loops = new Map<string, LoopHandle>()
    const executions = reactive(new Map<string, ExecutionEntry>())
    const inFlight = new Map<string, Promise<Execution | undefined>>()
    const loopOutputsByTaskRun = reactive(new Map<string, LoopOutputsEntry>())
    const loopOutputsInFlight = new Map<string, Promise<void>>()

    function taskType(taskId: string): string | undefined {
        return FlowUtils.findTaskById(executionsStore.flow, taskId)?.type
    }

    function hasFailure(node: TaskRunNode): boolean {
        if (isFailed(node.taskRun.state.current)) return true
        return node.kind === "loop" && loopHandle(loopRefOf(node)).failedCount.value !== 0
    }

    function focusRow(key: string) {
        nextTick(() => requestAnimationFrame(() => rowElement(key)?.focus()))
    }

    async function scrollRowIntoView(node: GanttNode) {
        await nextTick()
        let framesInView = 0
        for (let frame = 0; frame < MAX_REVEAL_FRAMES && framesInView < SETTLE_FRAMES; frame++) {
            await nextFrame()
            if (selected.value?.key !== node.key) return
            const row = rowElement(node.key)
            if (row) {
                row.scrollIntoView({block: "nearest"})
                framesInView++
            } else {
                framesInView = 0
                options.scrollToRow?.(node)
            }
        }
    }

    function select(node: GanttNode, selectOptions: {reveal?: boolean} = {}) {
        for (const ancestor of node.ancestors) expanded.add(ancestor.key)
        selected.value = node
        panelOpen.value = true
        if (selectOptions.reveal) scrollRowIntoView(node)
    }

    function closePanel() {
        panelOpen.value = false
        if (selected.value) focusRow(selected.value.key)
    }

    function reset() {
        selected.value = undefined
        panelOpen.value = false
        expanded.clear()
        loops.clear()
        executions.clear()
        inFlight.clear()
    }

    function refresh(): Promise<void> {
        const executionReloads = [...executions.entries()]
            .filter(([, entry]) => entry.execution)
            .map(([executionId]) => loadExecution(executionId, true))
        const loopReloads = [...loops.values()].map(handle => handle.reload())
        return Promise.all([...executionReloads, ...loopReloads]).then(() => undefined)
    }

    function loopHandle(loop: LoopRef): LoopHandle {
        const id = `${loop.parentExecutionId}:${loop.taskId}`
        let handle = loops.get(id)
        if (!handle) {
            handle = useLoopIterations(loop.parentExecutionId, loop.taskId, {failedOnly: failedOnly.value})
            loops.set(id, handle)
        }
        return handle
    }

    watch(failedOnly, (value) => {
        for (const handle of loops.values()) {
            handle.setFailedOnly(value)
            if (value) handle.loadCounts()
        }
    })

    function executionEntry(executionId: string): ExecutionEntry {
        let entry = executions.get(executionId)
        if (!entry) {
            executions.set(executionId, {loading: false})
            entry = executions.get(executionId)!
        }
        return entry
    }

    function loadExecution(executionId: string, force = false): Promise<Execution | undefined> {
        const entry = executionEntry(executionId)
        if (entry.execution && !force) return Promise.resolve(entry.execution)
        const pending = inFlight.get(executionId)
        if (pending) return pending

        entry.loading = true
        entry.error = undefined
        // Avoids executionsStore.loadExecution, which would overwrite the shared root execution.
        const request = ExecutionsAPI.execution({executionId}).then(
            (data) => {
                entry.execution = data as unknown as Execution
                return entry.execution
            },
            (error: unknown) => {
                entry.error = error
                return undefined
            },
        ).finally(() => {
            entry.loading = false
            inFlight.delete(executionId)
        })
        inFlight.set(executionId, request)
        return request
    }

    function needsLoopOutputs(cached: LoopOutputsEntry | undefined, terminal: boolean): boolean {
        if (!cached) return true
        const stale = Date.now() - cached.fetchedAt >= LOOP_OUTPUTS_REFRESH_MS
        if (terminal) return !cached.terminal && (!cached.failed || stale)
        return cached.terminal || stale
    }

    function loadLoopOutputs(node: TaskRunNode): Promise<void> {
        const taskRunId = node.taskRun.id
        const pending = loopOutputsInFlight.get(taskRunId)
        if (pending) return pending.then(() => loadLoopOutputs(node))

        const terminal = State.isTerminated(node.taskRun.state.current)
        const cached = loopOutputsByTaskRun.get(taskRunId)
        if (!needsLoopOutputs(cached, terminal)) return Promise.resolve()

        const request = loadTaskRunOutputs(node.executionId, taskRunId).then(
            (outputs): LoopOutputsEntry => ({outputs, fetchedAt: Date.now(), terminal, failed: false}),
            (): LoopOutputsEntry => ({outputs: cached?.outputs, fetchedAt: Date.now(), terminal: false, failed: true}),
        ).then((entry) => {
            loopOutputsByTaskRun.set(taskRunId, entry)
        }).finally(() => {
            loopOutputsInFlight.delete(taskRunId)
        })
        loopOutputsInFlight.set(taskRunId, request)
        return request
    }

    async function selectIteration(loopNode: TaskRunNode, row: LoopIterationRow) {
        const node = createIterationNode(loopRefOf(loopNode), row, loopAncestors(loopNode))
        select(node)

        const handle = loopHandle(node.loop)
        await handle.ensureLoaded()
        if (selected.value?.key === node.key && handle.iterations.value.some(loaded => loaded.id === row.id)) {
            scrollRowIntoView(node)
        }
    }

    async function siblingRow(node: IterationNode, delta: -1 | 1): Promise<LoopIterationRow | undefined> {
        const handle = loopHandle(node.loop)
        const loaded = loadedNeighbour(handle, node.row, delta)
        if (loaded.known) return loaded.row
        return handle.fetchNeighbour(node.row.index, delta, loaded.position).catch(() => undefined)
    }

    async function stepIteration(node: GanttNode, delta: -1 | 1) {
        const current = owningIteration(node)
        if (!current || stepping.value) return

        stepping.value = true
        try {
            const row = await siblingRow(current, delta)
            if (!row || selected.value?.key !== node.key) return

            const target = createIterationNode(current.loop, row, current.ancestors)
            if (node.kind === "iteration") {
                select(target, {reveal: true})
                return
            }

            const sibling = await loadExecution(row.id)
            if (selected.value?.key !== node.key) return

            const candidates = (sibling?.taskRunList ?? []) as ExecutionTaskRun[]
            const match = candidates.find(taskRun => taskRun.taskId === node.taskRun.taskId && taskRun.value === node.taskRun.value)
                ?? candidates.find(taskRun => taskRun.taskId === node.taskRun.taskId)
            if (!match) {
                select(target, {reveal: true})
                return
            }
            const owner = {id: row.id, namespace: current.loop.namespace, flowId: current.loop.flowId}
            select(createTaskRunNode(owner, match, taskType(match.taskId), taskRunAncestors(indexTaskRuns(candidates), match, iterationAncestors(target))), {reveal: true})
        } finally {
            stepping.value = false
        }
    }

    async function revealFirstFailure(start: TaskRunNode) {
        let node = start
        for (;;) {
            if (node.kind !== "loop") break

            const loop = loopRefOf(node)
            const handle = loopHandle(loop)
            if (handle.failedCount.value === undefined) await handle.loadCounts()
            if (!handle.failedCount.value) break

            expanded.add(node.key)
            await handle.setFailedOnly(true)
            await handle.ensureLoaded()
            const row = handle.iterations.value[0]
            if (!row) break

            const iteration = createIterationNode(loop, row, loopAncestors(node))
            expanded.add(iteration.key)
            const execution = await loadExecution(row.id)
            const failed = firstFailedLeaf((execution?.taskRunList ?? []) as ExecutionTaskRun[], taskType)
            if (!failed) {
                select(iteration, {reveal: true})
                return
            }
            const taskRuns = (execution?.taskRunList ?? []) as ExecutionTaskRun[]
            node = createTaskRunNode({id: row.id, namespace: loop.namespace, flowId: loop.flowId}, failed, taskType(failed.taskId), taskRunAncestors(indexTaskRuns(taskRuns), failed, iterationAncestors(iteration)))
        }
        select(node, {reveal: true})
    }

    return {
        selected,
        panelOpen,
        highlightedKey,
        failedOnly,
        compact: options.compact,
        levelFilter: options.levelFilter,
        stepping,
        select,
        closePanel,
        reset,
        refresh,
        isExpanded: (key) => expanded.has(key),
        setExpanded: (key, value) => {
            if (value) expanded.add(key)
            else expanded.delete(key)
        },
        toggleExpanded: (key) => {
            if (expanded.has(key)) expanded.delete(key)
            else expanded.add(key)
        },
        loopHandle,
        executionEntry,
        loadExecution,
        loopOutputs: (taskRunId) => loopOutputsByTaskRun.get(taskRunId),
        loadLoopOutputs,
        taskType,
        hasFailure,
        selectIteration,
        stepIteration,
        revealFirstFailure,
    }
}

export function provideGanttTree(options: Parameters<typeof createGanttTree>[0]): GanttTree {
    const tree = createGanttTree(options)
    provide(GANTT_TREE, tree)
    return tree
}
