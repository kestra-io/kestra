import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {ref} from "vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import GanttLoopStateBar from "./GanttLoopStateBar.vue"
import {GANTT_TREE, LOOP_OUTPUTS_REFRESH_MS, LOOP_TYPE, createGanttTree, createTaskRunNode, type GanttTree, type TaskRunNode} from "./ganttTree"
import type {ExecutionTaskRun} from "../executionTaskRun"

const loadTaskRunOutputs = vi.hoisted(() => vi.fn())

vi.mock("../../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs}))

const outputsByTaskRun: Record<string, Record<string, unknown>> = {
    "tr-a": {iterationCount: 100, runningIterations: 0, terminatedIterations: {SUCCESS: 97, FAILED: 3}},
    "tr-b": {iterationCount: 10, runningIterations: 2, terminatedIterations: {SUCCESS: 3}},
}

function loopNode(id: string, state: string): TaskRunNode {
    const taskRun = {id, taskId: `loop_${id}`, state: {current: state, histories: []}} as unknown as ExecutionTaskRun
    return createTaskRunNode({id: "root", namespace: "company.team", flowId: "monthly_close"}, taskRun, LOOP_TYPE, [])
}

let tree: GanttTree
let requestedAt: number[]

const initialOutputs = structuredClone(outputsByTaskRun)

beforeEach(() => {
    Object.assign(outputsByTaskRun, structuredClone(initialOutputs))
    vi.useFakeTimers()
    setActivePinia(createPinia())
    loadTaskRunOutputs.mockReset()
    requestedAt = []
    loadTaskRunOutputs.mockImplementation(async (_executionId: string, taskRunId: string) => {
        requestedAt.push(Date.now())
        return outputsByTaskRun[taskRunId] ?? {}
    })
    tree = createGanttTree({compact: ref(false), levelFilter: ref(undefined)})
})

afterEach(() => {
    vi.useRealTimers()
})

function mountBar(node: TaskRunNode) {
    return i18nMount(GanttLoopStateBar, {
        props: {node},
        global: {provide: {[GANTT_TREE as symbol]: tree}},
    })
}

const segmentsOf = (wrapper: ReturnType<typeof mountBar>) =>
    wrapper.findAll("[data-test='loop-state-bar-segment']").map(segment => [segment.attributes("data-state"), (segment.element as HTMLElement).style.flexGrow])

describe("GanttLoopStateBar", () => {
    it("shouldShowTheRunningAndNotStartedRemainderOfARunningLoop", async () => {
        const wrapper = mountBar(loopNode("tr-b", "RUNNING"))
        await flushPromises()

        expect(segmentsOf(wrapper)).toEqual([["SUCCESS", "3"], ["RUNNING", "2"], ["NOT_STARTED", "5"]])
    })

    it("shouldNotShowAnotherLoopsCountsWhenARecycledRowReceivesAnotherNode", async () => {
        const wrapper = mountBar(loopNode("tr-a", "SUCCESS"))
        await flushPromises()
        expect(segmentsOf(wrapper)).toEqual([["SUCCESS", "97"], ["FAILED", "3"]])

        const never = new Promise<Record<string, unknown>>(() => {})
        loadTaskRunOutputs.mockImplementation(() => never)
        await wrapper.setProps({node: loopNode("tr-b", "RUNNING")})
        expect(wrapper.find("[data-test='loop-state-bar']").exists()).toBe(false)

        loadTaskRunOutputs.mockImplementation(async (_executionId: string, taskRunId: string) => outputsByTaskRun[taskRunId])
        await wrapper.setProps({node: loopNode("tr-a", "SUCCESS")})
        expect(segmentsOf(wrapper)).toEqual([["SUCCESS", "97"], ["FAILED", "3"]])
    })

    it("shouldRenderNothingWhenTheLoopOutputsCarryNoCounts", async () => {
        loadTaskRunOutputs.mockResolvedValue({})
        const wrapper = mountBar(loopNode("tr-old", "SUCCESS"))
        await flushPromises()

        expect(wrapper.find("[data-test='loop-state-bar']").exists()).toBe(false)
    })

    it("shouldFetchOncePerRefreshWindowWhileRunningAndNeverOnceTerminal", async () => {
        const node = loopNode("tr-b", "RUNNING")
        const wrapper = mountBar(node)
        await flushPromises()
        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(LOOP_OUTPUTS_REFRESH_MS * 3)
        const requestTimes = loadTaskRunOutputs.mock.calls.map((_, index) => requestedAt[index])
        expect(requestTimes.length).toBeGreaterThanOrEqual(3)
        requestTimes.slice(1).forEach((time, index) => expect(time - requestTimes[index]).toBeGreaterThanOrEqual(LOOP_OUTPUTS_REFRESH_MS))

        await wrapper.setProps({node: loopNode("tr-b", "SUCCESS")})
        await flushPromises()
        const settled = loadTaskRunOutputs.mock.calls.length

        await vi.advanceTimersByTimeAsync(LOOP_OUTPUTS_REFRESH_MS * 5)
        await wrapper.setProps({node: loopNode("tr-b", "SUCCESS")})
        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(settled)
    })

    it("shouldRefetchWhenAFinishedLoopRestartsInPlace", async () => {
        const wrapper = mountBar(loopNode("tr-a", "SUCCESS"))
        await flushPromises()
        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(1)

        outputsByTaskRun["tr-a"] = {iterationCount: 100, runningIterations: 5, terminatedIterations: {SUCCESS: 10}}
        await wrapper.setProps({node: loopNode("tr-a", "RUNNING")})
        await flushPromises()

        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(2)
        expect(segmentsOf(wrapper)).toEqual([["SUCCESS", "10"], ["RUNNING", "5"], ["NOT_STARTED", "85"]])
    })

    it("shouldRefetchOnceWhenTheLoopTurnsTerminalWhileARequestIsInFlight", async () => {
        const stale = {iterationCount: 10, runningIterations: 4, terminatedIterations: {SUCCESS: 6}}
        const final = {iterationCount: 10, runningIterations: 0, terminatedIterations: {SUCCESS: 10}}
        let resolveFirst: (outputs: Record<string, unknown>) => void = () => {}
        loadTaskRunOutputs.mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
        loadTaskRunOutputs.mockImplementation(async () => final)

        const wrapper = mountBar(loopNode("tr-b", "RUNNING"))
        await flushPromises()
        await wrapper.setProps({node: loopNode("tr-b", "SUCCESS")})
        resolveFirst(stale)
        await flushPromises()

        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(2)
        expect(segmentsOf(wrapper)).toEqual([["SUCCESS", "10"]])
    })

    it("shouldRetryAfterTheRefreshWindowWhenTheOutputsRequestFailsOnAFinishedLoop", async () => {
        loadTaskRunOutputs.mockRejectedValueOnce(new Error("boom"))
        const node = loopNode("tr-a", "SUCCESS")

        await tree.loadLoopOutputs(node)
        await tree.loadLoopOutputs(node)
        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(LOOP_OUTPUTS_REFRESH_MS)
        await tree.loadLoopOutputs(node)

        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(2)
        expect(tree.loopOutputs("tr-a")?.outputs).toEqual(outputsByTaskRun["tr-a"])
    })

    it("shouldNotPollWhileTheLoopIsTerminalOrCarriesNoCounts", async () => {
        mountBar(loopNode("tr-a", "SUCCESS"))
        loadTaskRunOutputs.mockResolvedValue({})
        mountBar(loopNode("tr-old", "RUNNING"))
        await flushPromises()
        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(2)

        await vi.advanceTimersByTimeAsync(LOOP_OUTPUTS_REFRESH_MS * 4)

        expect(loadTaskRunOutputs).toHaveBeenCalledTimes(2)
        expect(vi.getTimerCount()).toBe(0)
    })
})
