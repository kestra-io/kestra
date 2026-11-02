import {beforeEach, describe, expect, it, vi} from "vitest"
import {nextTick, ref} from "vue"
import {createPinia, setActivePinia} from "pinia"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"
import {useExecutionsStore} from "../../../stores/executions"
import type {ExecutionTaskRun} from "../executionTaskRun"
import {
    LOOP_TYPE,
    createGanttTree,
    createIterationNode,
    createTaskRunNode,
    firstFailedLeaf,
    iterationAncestors,
    orderTaskRuns,
    type GanttTree,
    type LoopRef,
    type TaskRunNode,
} from "./ganttTree"

const mockExecution = vi.hoisted(() => vi.fn())
vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    execution: (...args: unknown[]) => mockExecution(...args),
}))

const histories = (state: string) => [
    {state: "CREATED", date: "2026-01-01T00:00:00Z"},
    {state, date: "2026-01-01T00:00:01Z"},
]

const taskRun = (id: string, taskId: string, state = "SUCCESS", parentTaskRunId?: string) =>
    ({id, taskId, parentTaskRunId, state: {current: state, histories: histories(state)}}) as unknown as ExecutionTaskRun

const iterationRow = (id: string, index: number, value: string, state = "SUCCESS") =>
    ({id, index, value, state: {current: state, histories: histories(state)}})

const executionRef = {id: "root", namespace: "company.team", flowId: "monthly_close"}
const loopRef: LoopRef = {parentExecutionId: "root", taskId: "per_region", namespace: "company.team", flowId: "monthly_close"}

const flow = {
    id: "monthly_close",
    namespace: "company.team",
    tasks: [{
        id: "per_region",
        type: LOOP_TYPE,
        tasks: [{
            id: "per_quarter",
            type: LOOP_TYPE,
            tasks: [{id: "reconcile", type: "io.kestra.plugin.core.execution.Fail"}],
        }],
    }],
} as unknown as FlowForExecution

function iterationsOf(rowsByTask: Record<string, ReturnType<typeof iterationRow>[]>, failedByTask: Record<string, number> = {}) {
    return async (options: Record<string, unknown>) => {
        const taskId = options["filters[taskId][EQUALS]"] as string
        const all = rowsByTask[taskId] ?? []
        const failedOnly = JSON.stringify(options["filters[state][IN]"]) === JSON.stringify(["FAILED"])
        if (options.onlyTotal) return failedOnly ? failedByTask[taskId] ?? 0 : all.length
        const results = failedOnly ? all.filter(row => row.state.current === "FAILED") : all
        return {
            results: results.map(row => ({id: row.id, state: row.state, loopRun: {index: row.index, value: row.value}})),
            total: results.length,
        }
    }
}

let tree: GanttTree

beforeEach(() => {
    setActivePinia(createPinia())
    mockExecution.mockReset()
    useExecutionsStore().flow = flow
    tree = createGanttTree({compact: ref(false), levelFilter: ref(undefined)})
})

describe("ganttTree selection", () => {
    it("shouldExpandAncestorsAndOpenPanelWhenSelectingNode", () => {
        const loop = createTaskRunNode(executionRef, taskRun("tr-loop", "per_region"), LOOP_TYPE, [])
        const iteration = createIterationNode(loopRef, iterationRow("it-3", 2, "APAC"), [{key: loop.key, label: "per_region"}])

        tree.select(iteration)

        expect(tree.selected.value?.key).toBe("it:it-3")
        expect(tree.panelOpen.value).toBe(true)
        expect(tree.isExpanded(loop.key)).toBe(true)
    })

    it("shouldKeepSelectionWhenClosingPanel", () => {
        const node = createTaskRunNode(executionRef, taskRun("tr-1", "extract"), undefined, [])
        tree.select(node)

        tree.closePanel()

        expect(tree.panelOpen.value).toBe(false)
        expect(tree.selected.value?.key).toBe(node.key)
    })
})

describe("ganttTree failed only", () => {
    it("shouldFilterEveryLoopHandleWhenGlobalFailedOnlyIsToggled", async () => {
        const findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions")
            .mockImplementation(iterationsOf({per_region: [iterationRow("it-1", 0, "EMEA"), iterationRow("it-3", 2, "APAC", "FAILED")]}) as never)
        const handle = tree.loopHandle(loopRef)
        await handle.ensureLoaded()
        expect(handle.iterations.value).toHaveLength(2)

        tree.failedOnly.value = true
        await nextTick()
        await vi.waitFor(() => expect(handle.iterations.value.map(row => row.value)).toEqual(["APAC"]))

        expect(findExecutions).toHaveBeenLastCalledWith(expect.objectContaining({"filters[state][IN]": ["FAILED"]}))
    })

    it("shouldStartNewLoopHandlesFilteredWhenGlobalFailedOnlyIsAlreadyOn", () => {
        tree.failedOnly.value = true

        expect(tree.loopHandle(loopRef).failedOnly.value).toBe(true)
    })
})

describe("ganttTree iteration stepping", () => {
    function reconcileIn(iterationId: string, state = "FAILED") {
        return {
            id: iterationId,
            namespace: "company.team",
            flowId: "monthly_close",
            taskRunList: [taskRun(`${iterationId}-extract`, "extract"), taskRun(`${iterationId}-reconcile`, "reconcile", state)],
        }
    }

    async function selectReconcileOf(iterationId: string, index: number, value: string) {
        const loop = createTaskRunNode(executionRef, taskRun("tr-loop", "per_region"), LOOP_TYPE, [])
        const iteration = createIterationNode(loopRef, iterationRow(iterationId, index, value, "FAILED"), [{key: loop.key, label: "per_region"}])
        const node = createTaskRunNode(
            {id: iterationId, namespace: "company.team", flowId: "monthly_close"},
            taskRun(`${iterationId}-reconcile`, "reconcile", "FAILED"),
            undefined,
            iterationAncestors(iteration),
        )
        tree.select(node)
        return node
    }

    it("shouldSelectSameTaskInNextSiblingIterationWhenSteppingForward", async () => {
        vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation(iterationsOf({
            per_region: [iterationRow("it-1", 0, "EMEA"), iterationRow("it-2", 1, "AMER"), iterationRow("it-3", 2, "APAC", "FAILED")],
        }) as never)
        await tree.loopHandle(loopRef).ensureLoaded()
        mockExecution.mockImplementation(async ({executionId}: {executionId: string}) => reconcileIn(executionId))
        const current = await selectReconcileOf("it-1", 0, "EMEA")

        await tree.stepIteration(current, 1)

        const selected = tree.selected.value as TaskRunNode
        expect(selected.taskRun.id).toBe("it-2-reconcile")
        expect(selected.executionId).toBe("it-2")
        expect(selected.ancestors.at(-1)?.iteration?.row.value).toBe("AMER")
        expect(tree.isExpanded("it:it-2")).toBe(true)
    })

    it("shouldHopFailureToFailureWhenLoopIsFilteredToFailedIterations", async () => {
        vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation(iterationsOf({
            per_region: [iterationRow("it-1", 0, "EMEA", "FAILED"), iterationRow("it-2", 1, "AMER"), iterationRow("it-3", 2, "APAC", "FAILED")],
        }) as never)
        const handle = tree.loopHandle(loopRef)
        await handle.setFailedOnly(true)
        await handle.ensureLoaded()
        mockExecution.mockImplementation(async ({executionId}: {executionId: string}) => reconcileIn(executionId))
        const current = await selectReconcileOf("it-1", 0, "EMEA")

        await tree.stepIteration(current, 1)

        expect((tree.selected.value as TaskRunNode).executionId).toBe("it-3")
    })

    it("shouldFallBackToIterationWhenSiblingHasNoSuchTask", async () => {
        vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation(iterationsOf({
            per_region: [iterationRow("it-1", 0, "EMEA"), iterationRow("it-2", 1, "AMER")],
        }) as never)
        await tree.loopHandle(loopRef).ensureLoaded()
        mockExecution.mockResolvedValue({id: "it-2", taskRunList: [taskRun("it-2-other", "other")]})
        const current = await selectReconcileOf("it-1", 0, "EMEA")

        await tree.stepIteration(current, 1)

        expect(tree.selected.value?.kind).toBe("iteration")
        expect(tree.selected.value?.key).toBe("it:it-2")
    })

    it("shouldKeepSelectionWhenThereIsNoSiblingToStepTo", async () => {
        vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation(iterationsOf({
            per_region: [iterationRow("it-1", 0, "EMEA")],
        }) as never)
        await tree.loopHandle(loopRef).ensureLoaded()
        const current = await selectReconcileOf("it-1", 0, "EMEA")

        await tree.stepIteration(current, -1)

        expect(tree.selected.value?.key).toBe(current.key)
    })
})

describe("ganttTree first failure", () => {
    it("shouldDescendThroughNestedLoopsToTheFailedLeafWhenRevealingFirstFailure", async () => {
        vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation(iterationsOf(
            {
                per_region: [iterationRow("it-emea", 0, "EMEA"), iterationRow("it-apac", 2, "APAC", "FAILED")],
                per_quarter: [iterationRow("it-q2", 1, "Q2"), iterationRow("it-q3", 2, "Q3", "FAILED")],
            },
            {per_region: 1, per_quarter: 1},
        ) as never)
        mockExecution.mockImplementation(async ({executionId}: {executionId: string}) => ({
            "it-apac": {id: "it-apac", taskRunList: [taskRun("tr-quarter", "per_quarter", "FAILED")]},
            "it-q3": {id: "it-q3", taskRunList: [taskRun("tr-extract", "extract"), taskRun("tr-reconcile", "reconcile", "FAILED")]},
        })[executionId])
        const start = createTaskRunNode(executionRef, taskRun("tr-region", "per_region", "FAILED"), LOOP_TYPE, [])

        await tree.revealFirstFailure(start)

        const selected = tree.selected.value as TaskRunNode
        expect(selected.taskRun.id).toBe("tr-reconcile")
        expect(selected.ancestors.map(ancestor => ancestor.label)).toEqual(["per_region", "#3 APAC", "per_quarter", "#3 Q3"])
        expect(tree.isExpanded("tr:tr-region")).toBe(true)
        expect(tree.isExpanded("it:it-apac")).toBe(true)
        expect(tree.loopHandle(loopRef).failedOnly.value).toBe(true)
    })

    it("shouldSelectTheLoopItselfWhenNoIterationFailed", async () => {
        vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation(iterationsOf({per_region: [iterationRow("it-1", 0, "EMEA")]}) as never)
        const start = createTaskRunNode(executionRef, taskRun("tr-region", "per_region", "FAILED"), LOOP_TYPE, [])

        await tree.revealFirstFailure(start)

        expect(tree.selected.value?.key).toBe(start.key)
    })
})

describe("ganttTree hidden task runs", () => {
    const FOR_EACH_ITEM = "io.kestra.plugin.core.flow.ForEachItem"
    const typeOf = (taskId: string) => ({
        per_file: FOR_EACH_ITEM,
        per_file_split: `${FOR_EACH_ITEM}$ForEachItemSplit`,
        per_file_items: `${FOR_EACH_ITEM}$ForEachItemExecutable`,
        per_file_merge: `${FOR_EACH_ITEM}$ForEachItemMergeOutputs`,
        notify: "io.kestra.plugin.core.log.Log",
    })[taskId]
    const runs = [
        taskRun("tr-files", "per_file", "FAILED"),
        taskRun("tr-split", "per_file_split", "SUCCESS", "tr-files"),
        taskRun("tr-items", "per_file_items", "FAILED", "tr-files"),
        taskRun("tr-merge", "per_file_merge", "SUCCESS", "tr-files"),
        taskRun("tr-notify", "notify"),
    ]

    it("shouldDropForEachItemInternalsWhenOrderingTaskRuns", () => {
        expect(orderTaskRuns(runs, typeOf).map(({task}) => task.taskId)).toEqual(["per_file", "notify"])
    })

    it("shouldPickTheForEachItemRowWhenItsHiddenExecutableIsTheFailedLeaf", () => {
        expect(firstFailedLeaf(runs, typeOf)?.id).toBe("tr-files")
    })
})

describe("ganttTree stepping past gaps and loaded pages", () => {
    const TOTAL = 12480

    function loopOf(indexes: number[], failed: (index: number) => boolean = () => false) {
        return vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation((async (options: Record<string, unknown>) => {
            const rows = options["filters[state][IN]"] ? indexes.filter(failed) : indexes
            if (options.onlyTotal) return rows.length
            const size = options.size as number
            const start = ((options.page as number) - 1) * size
            const results = rows.slice(start, start + size).map(index => {
                const state = failed(index) ? "FAILED" : "SUCCESS"
                return {id: `it-${index}`, state: {current: state, histories: histories(state)}, loopRun: {index, value: `v${index}`}}
            })
            return {results, total: rows.length}
        }) as never)
    }

    const allIndexes = Array.from({length: TOTAL}, (_, index) => index)

    it("shouldStepOverADeletedIterationInBothDirections", async () => {
        const findExecutions = loopOf(allIndexes.filter(index => index !== 3000))
        const after = createIterationNode(loopRef, iterationRow("it-3001", 3001, "v3001"), [])
        tree.select(after)

        await tree.stepIteration(after, -1)
        expect(tree.selected.value?.key).toBe("it:it-2999")
        await tree.stepIteration(tree.selected.value!, 1)
        expect(tree.selected.value?.key).toBe("it:it-3001")

        expect(findExecutions.mock.calls.length).toBeLessThan(40)
    })

    it("shouldStepToTheNeighbouringFailuresOfAnIterationBeyondTheLoadedPages", async () => {
        loopOf(allIndexes, index => index % 7 === 0)
        const handle = tree.loopHandle(loopRef)
        await handle.setFailedOnly(true)
        await handle.ensureLoaded()
        const current = createIterationNode(loopRef, iterationRow("it-7000", 7000, "v7000", "FAILED"), [])
        tree.select(current)

        await tree.stepIteration(current, -1)
        expect(tree.selected.value?.key).toBe("it:it-6993")
        await tree.stepIteration(tree.selected.value!, 1)
        await tree.stepIteration(tree.selected.value!, 1)
        expect(tree.selected.value?.key).toBe("it:it-7007")
    })
})

describe("ganttTree refresh", () => {
    it("shouldReloadCachedIterationExecutionsAndLoopRows", async () => {
        let state = "RUNNING"
        vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation((async (options: Record<string, unknown>) => options.onlyTotal
            ? 1
            : {results: [{id: "it-1", state: {current: state, histories: histories(state)}, loopRun: {index: 0, value: "EMEA"}}], total: 1}) as never)
        mockExecution.mockImplementation(async () => ({id: "it-1", state: {current: state}, taskRunList: [taskRun("it-1-extract", "extract", state)]}))
        const handle = tree.loopHandle(loopRef)
        await handle.ensureLoaded()
        await tree.loadExecution("it-1")
        state = "SUCCESS"

        await tree.refresh()

        expect(handle.iterations.value[0].state.current).toBe("SUCCESS")
        expect(tree.executionEntry("it-1").execution?.state.current).toBe("SUCCESS")
    })
})

describe("ganttTree large loops", () => {
    const TOTAL = 12480

    function largeLoop() {
        return vi.spyOn(useExecutionsStore(), "findExecutions").mockImplementation((async (options: Record<string, unknown>) => {
            if (options.onlyTotal) return TOTAL
            const size = options.size as number
            const start = ((options.page as number) - 1) * size
            const results = Array.from({length: Math.max(0, Math.min(size, TOTAL - start))}, (_, i) => {
                const index = start + i
                return {id: `it-${index}`, state: {current: "SUCCESS", histories: histories("SUCCESS")}, loopRun: {index, value: `v${index}`}}
            })
            return {results, total: TOTAL}
        }) as never)
    }

    const loopNode = () => createTaskRunNode(executionRef, taskRun("tr-loop", "per_region"), LOOP_TYPE, [])

    it("shouldMakeConstantRequestsWhenSelectingIterationFarFromTheLoadedPage", async () => {
        const findExecutions = largeLoop()

        await tree.selectIteration(loopNode(), iterationRow("it-5000", 5000, "v5000"))

        expect(tree.selected.value?.key).toBe("it:it-5000")
        expect(findExecutions.mock.calls.length).toBeLessThanOrEqual(1)
    })

    it("shouldFetchOnlyTheNeighbourPositionWhenSteppingFromAnUnloadedIteration", async () => {
        const findExecutions = largeLoop()
        const current = createIterationNode(loopRef, iterationRow("it-5000", 5000, "v5000"), [])
        tree.select(current)

        await tree.stepIteration(current, 1)

        expect(tree.selected.value?.key).toBe("it:it-5001")
        expect(findExecutions.mock.calls.map(([options]) => [options.page, options.size])).toEqual([[5002, 1]])
    })

    it("shouldIgnoreSecondStepWhileOneIsInFlight", async () => {
        const findExecutions = largeLoop()
        const current = createIterationNode(loopRef, iterationRow("it-5000", 5000, "v5000"), [])
        tree.select(current)

        await Promise.all([tree.stepIteration(current, 1), tree.stepIteration(current, 1)])

        expect(findExecutions).toHaveBeenCalledTimes(1)
    })

    it("shouldKeepNewSelectionWhenStepResolvesAfterSelectionChanged", async () => {
        largeLoop()
        const current = createIterationNode(loopRef, iterationRow("it-5000", 5000, "v5000"), [])
        tree.select(current)
        const other = createTaskRunNode(executionRef, taskRun("tr-1", "extract"), undefined, [])

        const step = tree.stepIteration(current, 1)
        tree.select(other)
        await step

        expect(tree.selected.value?.key).toBe(other.key)
    })
})
