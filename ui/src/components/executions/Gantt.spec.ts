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

describe("Gantt subflow logs", () => {
    afterAll(() => localStorage.clear())

    it("shouldSelectEachChildExecutionWithoutParentTaskFiltersWhenExpandingLoopIterations", async () => {
        const pinia = createPinia()
        const store = useExecutionsStore(pinia)
        store.flow = {
            id: "parent",
            namespace: "company.team",
            tasks: [
                {id: "call_child", type: "io.kestra.plugin.core.flow.Subflow"},
                {id: "summary", type: "io.kestra.plugin.core.log.Log"},
            ],
        } as FlowForExecution
        store.execution = {
            id: "parent-execution",
            namespace: "company.team",
            flowId: "parent",
            state: {current: "SUCCESS", histories},
            taskRunList: [
                ...["a", "b"].map(value => ({
                    id: `parent-task-${value}`,
                    taskId: "call_child",
                    value,
                    outputs: {executionId: `child-${value}`},
                    state: {current: "SUCCESS", histories},
                })),
                {id: "summary-task", taskId: "summary", state: {current: "SUCCESS", histories}},
            ],
        } as Execution
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: "/", component: {template: "<div />"}}],
        })
        await router.push("/")
        const wrapper = i18nMount(Gantt, {
            global: {
                plugins: [pinia, router],
                stubs: {
                    KSFilter: true,
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
