import {afterAll, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createApp} from "vue"
import {createMemoryHistory, createRouter} from "vue-router"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {useExecutionsStore, type Execution} from "../../stores/executions"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"
import Gantt from "../../components/executions/Gantt.vue"

vi.mock("../../components/logs/TaskRunDetails.vue", () => ({
    default: {
        name: "TaskRunDetails",
        props: ["taskRunId", "targetExecutionId", "targetFlow", "hideTaskHeader"],
        template: "<div data-test='task-logs' />",
    },
}))

const mockExecution = vi.hoisted(() => vi.fn())
vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    execution: (...args: unknown[]) => mockExecution(...args),
}))

const histories = [
    {state: "RUNNING", date: "2026-10-08T00:00:00Z"},
    {state: "SUCCESS", date: "2026-10-08T00:00:01Z"},
]

async function mountGantt(
    flowTasks: Array<Record<string, unknown>>,
    taskRunList: Array<Record<string, unknown>>,
    flowLoadsLast = false,
    findExecutions: (params: Record<string, unknown>) => Promise<unknown> = async () => 0,
) {
    const pinia = createPinia()
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: "/", component: {template: "<div />"}},
            {path: "/executions/:namespace/:flowId/:id", name: "executions/update/gantt", component: {template: "<div />"}},
            {path: "/executions", name: "executions/list", component: {template: "<div />"}},
        ],
    })
    // The store reads the route at setup, so it is created under an app that carries the router.
    createApp({}).use(pinia).use(router)
    const store = useExecutionsStore(pinia)
    vi.spyOn(store, "findExecutions").mockImplementation(findExecutions as never)
    const flow = {id: "flow", namespace: "company.team", tasks: flowTasks} as FlowForExecution
    if (!flowLoadsLast) store.flow = flow
    store.execution = {
        id: "execution",
        namespace: "company.team",
        flowId: "flow",
        state: {current: "SUCCESS", histories},
        taskRunList,
    } as Execution
    await router.push("/")
    const wrapper = i18nMount(Gantt, {
        global: {
            plugins: [pinia, router],
            stubs: {
                KSFilter: true,
                TaskRunActions: true,
                TaskIcon: true,
                Duration: true,
                KsSplitter: {template: "<div><slot /></div>"},
                KsSplitterPanel: {template: "<div><slot /></div>"},
                DynamicScroller: {
                    props: ["items"],
                    template: "<div><slot v-for='(item, index) in items' :item='item' :index='index' :active='true' /></div>",
                },
                DynamicScrollerItem: {template: "<div><slot /></div>"},
            },
        },
    })
    await flushPromises()
    if (flowLoadsLast) {
        store.flow = flow
        await flushPromises()
    }
    return {wrapper, store}
}

const logTask = (id: string) => ({id, type: "io.kestra.plugin.core.log.Log"})

describe("Gantt layout", () => {
    it("shouldShowOffsetAndScaleForEachTaskRun", async () => {
        const {wrapper} = await mountGantt([logTask("first")], [
            {
                id: "tr-1",
                taskId: "first",
                state: {
                    current: "SKIPPED",
                    histories: [
                        {state: "CREATED", date: "2026-10-08T00:00:00.500Z"},
                        {state: "SKIPPED", date: "2026-10-08T00:00:00.800Z"},
                    ],
                },
            },
        ])
        try {
            expect(wrapper.get("[data-test='gantt-task-offset']").text()).toBe("+0.50s")
            expect(wrapper.get("[data-test='gantt-scale']").text()).toBe("0s0.80s")
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldDrawBarsOnOneScaleAndLeaveWaitBeforeRunningAsGap", async () => {
        const {wrapper} = await mountGantt([logTask("first"), logTask("second")], [
            {
                id: "tr-1",
                taskId: "first",
                state: {
                    current: "SUCCESS",
                    histories: [
                        {state: "CREATED", date: "2026-10-08T00:00:00Z"},
                        {state: "RUNNING", date: "2026-10-08T00:00:00Z"},
                        {state: "SUCCESS", date: "2026-10-08T00:00:01Z"},
                    ],
                },
            },
            {
                id: "tr-2",
                taskId: "second",
                state: {
                    current: "SUCCESS",
                    histories: [
                        {state: "CREATED", date: "2026-10-08T00:00:01Z"},
                        {state: "RUNNING", date: "2026-10-08T00:00:02Z"},
                        {state: "SUCCESS", date: "2026-10-08T00:00:04Z"},
                    ],
                },
            },
        ])
        try {
            const bars = wrapper.findAll<HTMLElement>("[data-test='gantt-task-bar']")
            expect(bars).toHaveLength(2)
            expect([bars[0].element.style.left, bars[0].element.style.width]).toEqual(["0%", "25%"])
            expect([bars[1].element.style.left, bars[1].element.style.width]).toEqual(["50%", "50%"])
            expect(wrapper.get("[data-test='gantt-scale']").text()).toBe("0s4s")
        } finally {
            wrapper.unmount()
        }
    })
})

describe("Gantt selection", () => {
    afterAll(() => localStorage.clear())

    const rowOf = (wrapper: Awaited<ReturnType<typeof mountGantt>>["wrapper"], index: number) =>
        wrapper.findAll("[role='row']")[index]

    it("shouldOpenPanelOnlyForSelectedRowAndKeepRowsCollapsedWhenRowsAreClicked", async () => {
        const {wrapper, store} = await mountGantt(
            [logTask("first"), logTask("second")],
            [
                {id: "tr-1", taskId: "first", state: {current: "SUCCESS", histories}},
                {id: "tr-2", taskId: "second", state: {current: "SUCCESS", histories}},
            ],
        )
        try {
            expect(wrapper.find("[data-test='gantt-run-panel']").exists()).toBe(false)

            await rowOf(wrapper, 0).trigger("click")
            await rowOf(wrapper, 1).trigger("click")

            expect(rowOf(wrapper, 0).attributes("aria-selected")).toBe("false")
            expect(rowOf(wrapper, 1).attributes("aria-selected")).toBe("true")
            const viewers = wrapper.findAllComponents({name: "TaskRunDetails"})
            expect(viewers).toHaveLength(1)
            expect(viewers[0].props("taskRunId")).toBe("tr-2")
            expect(viewers[0].props("targetFlow")).toEqual(store.flow)
            expect(viewers[0].props("hideTaskHeader")).toBe(true)
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldShowChildExecutionLogsInPanelWhenSubflowRowIsSelected", async () => {
        const {wrapper} = await mountGantt(
            [{id: "call_child", type: "io.kestra.plugin.core.flow.Subflow"}],
            [{id: "parent-task", taskId: "call_child", outputs: {executionId: "child-a"}, state: {current: "SUCCESS", histories}}],
        )
        try {
            await rowOf(wrapper, 0).trigger("click")

            const viewer = wrapper.getComponent({name: "TaskRunDetails"})
            expect(viewer.props("targetExecutionId")).toBe("child-a")
            expect(viewer.props("taskRunId")).toBeUndefined()
            expect(viewer.props("hideTaskHeader")).toBe(false)
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldSelectRowWhenEnterIsPressedOnIt", async () => {
        const {wrapper} = await mountGantt([logTask("first")], [{id: "tr-1", taskId: "first", state: {current: "SUCCESS", histories}}])
        try {
            await rowOf(wrapper, 0).trigger("keydown", {key: "Enter"})

            expect(rowOf(wrapper, 0).attributes("aria-selected")).toBe("true")
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldRenderChevronOnlyOnLoopRows", async () => {
        const {wrapper} = await mountGantt(
            [logTask("first"), {id: "per_region", type: "io.kestra.plugin.core.flow.Loop", tasks: []}],
            [
                {id: "tr-1", taskId: "first", state: {current: "SUCCESS", histories}},
                {id: "tr-2", taskId: "per_region", state: {current: "SUCCESS", histories}},
            ],
        )
        try {
            expect(rowOf(wrapper, 0).find("[data-test='gantt-row-chevron']").exists()).toBe(false)
            expect(rowOf(wrapper, 1).find("[data-test='gantt-row-chevron']").exists()).toBe(true)
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldRenderChevronOnLoopRowWhenFlowIsLoadedAfterTheExecution", async () => {
        const {wrapper} = await mountGantt(
            [{id: "per_region", type: "io.kestra.plugin.core.flow.Loop", tasks: []}],
            [{id: "tr-1", taskId: "per_region", state: {current: "SUCCESS", histories}}],
            true,
        )
        try {
            expect(rowOf(wrapper, 0).find("[data-test='gantt-row-chevron']").exists()).toBe(true)
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldExpandLoopRowWhenItsFailedChipIsActivated", async () => {
        const {wrapper} = await mountGantt(
            [{id: "per_file", type: "io.kestra.plugin.core.flow.Loop", tasks: []}],
            [{id: "tr-1", taskId: "per_file", state: {current: "SUCCESS", histories}}],
            false,
            async (params) => (params.onlyTotal ? 2 : {results: [], total: 2}),
        )
        try {
            const chevron = rowOf(wrapper, 0).get("[data-test='gantt-row-chevron']")
            expect(chevron.attributes("aria-expanded")).toBe("false")

            await wrapper.get("[data-test='gantt-loop-failed']").trigger("click")
            await flushPromises()

            expect(chevron.attributes("aria-expanded")).toBe("true")
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldOnlyKeepFailedRowsWhenFailedOnlyIsToggled", async () => {
        const {wrapper} = await mountGantt(
            [logTask("first"), logTask("second")],
            [
                {id: "tr-1", taskId: "first", state: {current: "SUCCESS", histories}},
                {id: "tr-2", taskId: "second", state: {current: "FAILED", histories}},
            ],
        )
        try {
            expect(wrapper.findAll("[role='row']")).toHaveLength(2)

            await wrapper.get("[data-test='gantt-failed-only']").trigger("click")

            const rows = wrapper.findAll("[role='row']")
            expect(rows).toHaveLength(1)
            expect(rows[0].attributes("data-row-key")).toBe("tr:tr-2")
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldSelectFailedTaskRunWhenGoToFirstFailureIsClicked", async () => {
        const {wrapper} = await mountGantt(
            [logTask("first"), logTask("second")],
            [
                {id: "tr-1", taskId: "first", state: {current: "SUCCESS", histories}},
                {id: "tr-2", taskId: "second", state: {current: "FAILED", histories}},
            ],
        )
        try {
            await wrapper.get("[data-test='gantt-first-failure']").trigger("click")
            await flushPromises()

            expect(rowOf(wrapper, 1).attributes("aria-selected")).toBe("true")
        } finally {
            wrapper.unmount()
        }
    })
})

describe("Gantt panel keyboard shortcuts", () => {
    afterAll(() => localStorage.clear())

    const press = async (key: string, target: EventTarget = document) => {
        target.dispatchEvent(new KeyboardEvent("keydown", {key, bubbles: true}))
        await flushPromises()
    }

    async function mountLoopWithIterations(total = 40) {
        mockExecution.mockImplementation(async ({executionId}: {executionId: string}) => ({id: executionId, taskRunList: []}))
        const requests: Array<Record<string, unknown>> = []
        const mounted = await mountGantt(
            [{id: "per_region", type: "io.kestra.plugin.core.flow.Loop", tasks: []}],
            [{id: "tr-1", taskId: "per_region", state: {current: "SUCCESS", histories}}],
            false,
            async (params) => {
                if (params.onlyTotal) return total
                requests.push(params)
                const size = params.size as number
                const start = ((params.page as number) - 1) * size
                return {
                    results: Array.from({length: Math.max(0, Math.min(size, total - start))}, (_, i) => ({
                        id: `it-${start + i}`,
                        state: {current: "SUCCESS", histories},
                        loopRun: {index: start + i, value: `v${start + i}`},
                    })),
                    total,
                }
            },
        )
        await mounted.wrapper.get("[data-test='gantt-row-chevron']").trigger("click")
        await flushPromises()
        return {...mounted, requests}
    }

    it("shouldNotClaimZeroIterationsInPanelHeaderWhileCountsAreUnknown", async () => {
        const {wrapper} = await mountGantt(
            [{id: "per_region", type: "io.kestra.plugin.core.flow.Loop", tasks: []}],
            [{id: "tr-1", taskId: "per_region", state: {current: "SUCCESS", histories}}],
            false,
            async () => { throw new Error("counts unavailable") },
        )
        try {
            await wrapper.get("[role='row']").trigger("click")
            await flushPromises()

            expect(wrapper.get("[data-test='gantt-panel-meta']").text()).not.toContain("gantt_loop_iterations_count")
        } finally {
            wrapper.unmount()
        }
    })

    const panelTitle = (wrapper: Awaited<ReturnType<typeof mountGantt>>["wrapper"]) => wrapper.get("[data-test='gantt-run-panel'] h2").text()

    it("shouldStepToNextIterationWhenClosingBracketIsPressed", async () => {
        const {wrapper} = await mountLoopWithIterations()
        try {
            await wrapper.findAll("[data-test='loop-iteration-row']")[2].trigger("click")
            expect(panelTitle(wrapper)).toBe("v2")

            await press("]")
            expect(panelTitle(wrapper)).toBe("v3")
            await press("[")
            await press("[")

            expect(panelTitle(wrapper)).toBe("v1")
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldFetchNeighbourOnceWhenKeyIsHeldDown", async () => {
        const {wrapper, requests} = await mountLoopWithIterations()
        try {
            await wrapper.findAll("[data-test='loop-iteration-row']")[9].trigger("click")
            requests.length = 0

            document.dispatchEvent(new KeyboardEvent("keydown", {key: "]"}))
            document.dispatchEvent(new KeyboardEvent("keydown", {key: "]"}))
            await flushPromises()

            expect(requests.filter(request => request.size === 1)).toHaveLength(1)
            expect(panelTitle(wrapper)).toBe("v10")
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldCloseOnEscapeButNotWhileTypingInAnInput", async () => {
        const {wrapper} = await mountGantt([logTask("first")], [{id: "tr-1", taskId: "first", state: {current: "SUCCESS", histories}}])
        const input = document.createElement("input")
        document.body.appendChild(input)
        try {
            await wrapper.get("[role='row']").trigger("click")

            await press("Escape", input)
            expect(wrapper.find("[data-test='gantt-run-panel']").exists()).toBe(true)

            await press("Escape")
            expect(wrapper.find("[data-test='gantt-run-panel']").exists()).toBe(false)
        } finally {
            input.remove()
            wrapper.unmount()
        }
    })

    it("shouldNotStepWhileTypingInAnInput", async () => {
        const {wrapper} = await mountLoopWithIterations()
        const input = document.createElement("input")
        document.body.appendChild(input)
        try {
            await wrapper.findAll("[data-test='loop-iteration-row']")[2].trigger("click")

            await press("]", input)

            expect(panelTitle(wrapper)).toBe("v2")
        } finally {
            input.remove()
            wrapper.unmount()
        }
    })
})
