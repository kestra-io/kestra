import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {setActivePinia} from "pinia"
import {ref} from "vue"
import {routedPinia} from "../../../../tests/unit/memoryRouter"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {useExecutionsStore} from "../../../stores/executions"
import GanttPanelLoop from "./GanttPanelLoop.vue"
import {GANTT_TREE, LOOP_OUTPUTS_REFRESH_MS, LOOP_TYPE, createGanttTree, createTaskRunNode, type GanttTree} from "./ganttTree"
import type {ExecutionTaskRun} from "../executionTaskRun"

const loadTaskRunOutputs = vi.hoisted(() => vi.fn())
vi.mock("../../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs}))

const iteration = (index: number, state: string) => ({
    id: `it-${index}`,
    state: {current: state, histories: [{state: "RUNNING", date: "2026-01-01T00:00:00Z"}]},
    loopRun: {index, value: String(index + 1)},
})

const loopNode = (state: string) => createTaskRunNode(
    {id: "root", namespace: "qa.loop.gantt", flowId: "loop_plain_concurrency_gantt"},
    {id: "tr-loop", taskId: "loop", state: {current: state, histories: []}} as unknown as ExecutionTaskRun,
    LOOP_TYPE,
    [],
)

let tree: GanttTree
let findExecutions: ReturnType<typeof vi.spyOn>
let iterations: ReturnType<typeof iteration>[]

beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(routedPinia())
    iterations = [iteration(0, "RUNNING"), iteration(1, "RUNNING")]
    findExecutions = vi.spyOn(useExecutionsStore(), "findExecutions")
    findExecutions.mockImplementation((async (options: Record<string, unknown>) => {
        const states = options["filters[state][IN]"] as string[] | undefined
        const matching = iterations.filter(row => !states || states.includes(row.state.current))
        return options.onlyTotal ? matching.length : {results: matching, total: matching.length}
    }) as never)
    loadTaskRunOutputs.mockReset()
    loadTaskRunOutputs.mockImplementation(async () => ({
        iterationCount: 6,
        runningIterations: iterations.filter(row => row.state.current === "RUNNING").length,
        terminatedIterations: {SUCCESS: iterations.filter(row => row.state.current === "SUCCESS").length},
    }))
    tree = createGanttTree({compact: ref(false), levelFilter: ref(undefined)})
})

afterEach(() => {
    vi.useRealTimers()
})

function mountPanel(state: string) {
    return i18nMount(GanttPanelLoop, {
        props: {node: loopNode(state)},
        global: {
            provide: {[GANTT_TREE as symbol]: tree},
            stubs: {Duration: true, RouterLink: {template: "<a><slot /></a>"}},
        },
    })
}

const rowTexts = (wrapper: ReturnType<typeof mountPanel>) =>
    wrapper.findAll("[data-test='gantt-loop-iteration']").map(row => row.text())

describe("GanttPanelLoop", () => {
    it("shouldFollowTheIterationsToTheEndOfTheLoopWithoutAReload", async () => {
        const wrapper = mountPanel("RUNNING")
        await flushPromises()
        expect(rowTexts(wrapper)).toEqual(["#11Running", "#22Running"])

        iterations = Array.from({length: 6}, (_, index) => iteration(index, "SUCCESS"))
        await wrapper.setProps({node: loopNode("SUCCESS")})
        await vi.advanceTimersByTimeAsync(LOOP_OUTPUTS_REFRESH_MS)

        expect(rowTexts(wrapper)).toEqual(["#11Success", "#22Success", "#33Success", "#44Success", "#55Success", "#66Success"])

        const settled = findExecutions.mock.calls.length
        await vi.advanceTimersByTimeAsync(LOOP_OUTPUTS_REFRESH_MS * 3)
        expect(findExecutions).toHaveBeenCalledTimes(settled)
    })
})
