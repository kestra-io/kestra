import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {defineComponent, type PropType} from "vue"
import {createPinia} from "pinia"
import {createApp} from "vue"
import {createMemoryHistory, createRouter} from "vue-router"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {useExecutionsStore, type Execution} from "../../stores/executions"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"
import Gantt from "../../components/executions/Gantt.vue"

const toastError = vi.hoisted(() => vi.fn())

vi.mock("../../utils/toast", () => ({
    useToast: () => ({success: vi.fn(), error: toastError}),
}))

vi.mock("../../components/logs/TaskRunDetails.vue", () => ({
    default: {
        name: "TaskRunDetails",
        props: ["taskRunId", "targetExecutionId", "targetFlow", "hideTaskHeader"],
        template: "<div data-test='task-logs' />",
    },
}))

afterAll(() => localStorage.clear())

const histories = [
    {state: "RUNNING", date: "2026-10-08T00:00:00Z"},
    {state: "SUCCESS", date: "2026-10-08T00:00:01Z"},
]

async function mountGantt(flowTasks: Array<Record<string, unknown>>, taskRunList: Array<Record<string, unknown>>, props: {showBanner?: boolean} = {}) {
    const pinia = createPinia()
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: "/", component: {template: "<div />"}}],
    })
    // The store reads the route at setup, so it is created under an app that carries the router.
    createApp({}).use(pinia).use(router)
    const store = useExecutionsStore(pinia)
    store.flow = {id: "flow", namespace: "company.team", tasks: flowTasks} as FlowForExecution
    store.execution = {
        id: "execution",
        namespace: "company.team",
        flowId: "flow",
        state: {current: "SUCCESS", histories},
        taskRunList,
    } as Execution
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

interface GanttTableOptions {
    refresh?: {callback?: () => Promise<void>};
}

const KsFilterStub = defineComponent({
    name: "KsFilterStub",
    props: {
        tableOptions: {
            type: Object as PropType<GanttTableOptions>,
            required: true,
        },
    },
    template: "<div />",
})

const TaskRunActionsStub = defineComponent({
    name: "TaskRunActionsStub",
    props: {
        taskRun: {
            type: Object,
            required: true,
        },
    },
    template: `
        <div
            data-test="task-run-actions"
            :data-state="taskRun.state.current"
            :data-attempts="taskRun.attempts ? taskRun.attempts.length : 0"
        />
    `,
})

function createExecution(taskState: "FAILED" | "SUCCESS", attempts: number): Execution {
    return {
        id: "execution-id",
        namespace: "company.team",
        flowId: "flow-id",
        flowRevision: 1,
        state: {current: taskState, histories},
        taskRunList: [{
            id: "task-run-id",
            taskId: "task-id",
            state: {
                current: taskState,
                histories: [
                    {state: "RUNNING", date: "2026-10-08T00:00:00Z"},
                    {state: taskState, date: "2026-10-08T00:00:01Z"},
                ],
            },
            attempts: Array.from({length: attempts}, () => ({
                state: {
                    current: taskState,
                    histories: [{state: taskState, date: "2026-10-08T00:00:01Z"}],
                },
            })),
        }],
    } as Execution
}

function deferred<T>() {
    let resolve!: (value: T) => void
    let reject!: (reason?: unknown) => void
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise
        reject = rejectPromise
    })
    return {promise, resolve, reject}
}

async function mountRefreshGantt(initialExecution: Execution) {
    const pinia = createPinia()
    const store = useExecutionsStore(pinia)
    store.flow = {
        id: "flow-id",
        namespace: "company.team",
        tasks: [{id: "task-id", type: "io.kestra.plugin.core.log.Log"}],
    } as FlowForExecution
    store.execution = initialExecution

    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: "/", component: {template: "<div />"}}],
    })
    await router.push("/")

    const wrapper = i18nMount(Gantt, {
        global: {
            plugins: [pinia, router],
            stubs: {
                KsFilter: KsFilterStub,
                TaskRunActions: TaskRunActionsStub,
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

function refreshCallback(wrapper: VueWrapper): () => Promise<void> {
    return wrapper.findComponent(KsFilterStub).props("tableOptions").refresh!.callback!
}

describe("Gantt subflow logs", () => {
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

describe("Gantt refresh", () => {
    beforeEach(() => toastError.mockReset())

    it("shouldReloadTheCurrentExecutionAndRecomputeFromTheLatestResponse", async () => {
        const {wrapper, store} = await mountRefreshGantt(createExecution("FAILED", 1))
        const latestExecution = createExecution("SUCCESS", 2)
        const refresh = deferred<Execution>()
        const loadExecution = vi.spyOn(store, "loadExecution").mockImplementation(({id}) =>
            refresh.promise.then((response) => {
                expect(id).toBe("execution-id")
                store.execution = response
                return response
            }),
        )

        try {
            let settled = false
            const refreshPromise = refreshCallback(wrapper)().finally(() => {
                settled = true
            })

            expect(loadExecution).toHaveBeenCalledWith({id: "execution-id"})
            expect(wrapper.get("[data-test='task-run-actions']").attributes("data-state")).toBe("FAILED")
            expect(settled).toBe(false)

            refresh.resolve(latestExecution)
            await refreshPromise
            await flushPromises()

            const task = wrapper.get("[data-test='task-run-actions']")
            expect(task.attributes("data-state")).toBe("SUCCESS")
            expect(task.attributes("data-attempts")).toBe("2")
            expect(settled).toBe(true)
        } finally {
            wrapper.unmount()
        }
    })

    it("shouldPreserveTheChartAndReportARefreshFailure", async () => {
        const {wrapper, store} = await mountRefreshGantt(createExecution("FAILED", 1))
        const failure = Object.assign(new Error("Refresh failed"), {
            status: 400,
            problem: {type: "about:blank", title: "Bad request", detail: "Refresh failed"},
            response: {data: {type: "about:blank", title: "Bad request", detail: "Refresh failed"}},
        })
        vi.spyOn(store, "loadExecution").mockRejectedValue(failure)

        try {
            await expect(refreshCallback(wrapper)()).resolves.toBeUndefined()
            await flushPromises()

            const task = wrapper.get("[data-test='task-run-actions']")
            expect(task.attributes("data-state")).toBe("FAILED")
            expect(task.attributes("data-attempts")).toBe("1")
            expect(toastError).toHaveBeenCalledWith("Refresh failed")
        } finally {
            wrapper.unmount()
        }
    })
})
