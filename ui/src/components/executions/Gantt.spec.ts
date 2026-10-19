import {afterAll, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
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

const histories = [
    {state: "RUNNING", date: "2026-10-08T00:00:00Z"},
    {state: "SUCCESS", date: "2026-10-08T00:00:01Z"},
]

async function mountGantt(flowTasks: Array<Record<string, unknown>>, taskRunList: Array<Record<string, unknown>>, props: {showBanner?: boolean} = {}) {
    const pinia = createPinia()
    const store = useExecutionsStore(pinia)
    store.flow = {id: "flow", namespace: "company.team", tasks: flowTasks} as FlowForExecution
    store.execution = {
        id: "execution",
        namespace: "company.team",
        flowId: "flow",
        state: {current: "SUCCESS", histories},
        taskRunList,
    } as Execution
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: "/", component: {template: "<div />"}}],
    })
    await router.push("/")
    const wrapper = i18nMount(Gantt, {
        props,
        global: {
            plugins: [pinia, router],
            stubs: {
                KSFilter: true,
                ExecutionSummaryCard: {template: "<div data-test='execution-summary-card' />"},
                TaskRunActions: true,
                TaskIcon: true,
                Duration: true,
                DynamicScroller: {
                    props: ["items"],
                    template: "<div><slot v-for='(item, index) in items' :item='item' :index='index' :active='true' /></div>",
                },
                DynamicScrollerItem: {template: "<div><slot /></div>"},
            },
        },
    })
    await flushPromises()
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

describe("Gantt subflow logs", () => {
    afterAll(() => localStorage.clear())

    it("shouldSelectEachChildExecutionWithoutParentTaskFiltersWhenExpandingLoopIterations", async () => {
        const {wrapper, store} = await mountGantt(
            [
                {id: "call_child", type: "io.kestra.plugin.core.flow.Subflow"},
                {id: "summary", type: "io.kestra.plugin.core.log.Log"},
            ],
            [
                ...["a", "b"].map(value => ({
                    id: `parent-task-${value}`,
                    taskId: "call_child",
                    value,
                    outputs: {executionId: `child-${value}`},
                    state: {current: "SUCCESS", histories},
                })),
                {id: "summary-task", taskId: "summary", state: {current: "SUCCESS", histories}},
            ],
        )
        try {
            for (const label of wrapper.findAll("[data-test='gantt-task-label']")) {
                await label.trigger("click")
            }
            const viewers = wrapper.findAllComponents({name: "TaskRunDetails"})
            expect(viewers).toHaveLength(3)
            for (const [index, childId] of ["child-a", "child-b"].entries()) {
                expect(viewers[index].props("targetExecutionId")).toBe(childId)
                expect(viewers[index].props("taskRunId")).toBeUndefined()
                expect(viewers[index].props("targetFlow")).toBeUndefined()
                expect(viewers[index].props("hideTaskHeader")).toBe(false)
            }
            expect(viewers[2].props("targetExecutionId")).toBeUndefined()
            expect(viewers[2].props("taskRunId")).toBe("summary-task")
            expect(viewers[2].props("targetFlow")).toEqual(store.flow)
            expect(viewers[2].props("hideTaskHeader")).toBe(true)
        } finally {
            wrapper.unmount()
        }
    })
})

describe("Gantt summary card", () => {
    afterAll(() => localStorage.clear())

    const taskRuns = [{id: "tr-1", taskId: "first", state: {current: "SUCCESS", histories}}]

    it("shouldShowTheSummaryCardOnlyWhenShowBannerIsSet", async () => {
        const {wrapper: withBanner} = await mountGantt([logTask("first")], taskRuns, {showBanner: true})
        const {wrapper: embedded} = await mountGantt([logTask("first")], taskRuns)
        try {
            expect(withBanner.find("[data-test='execution-summary-card']").exists()).toBe(true)
            expect(embedded.find("[data-test='execution-summary-card']").exists()).toBe(false)
        } finally {
            withBanner.unmount()
            embedded.unmount()
        }
    })
})
