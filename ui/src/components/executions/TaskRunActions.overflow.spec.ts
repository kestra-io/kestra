import {describe, expect, it} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import {KsDropdownItem} from "@kestra-io/design-system"
import type {State, StateType} from "@kestra-io/kestra-sdk"
import type {Execution} from "../../stores/executions"
import TaskRunActions from "./TaskRunActions.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

function state(current: StateType): State {
    return {current, histories: []}
}

const execution = {id: "execution", namespace: "tests", flowId: "flow", flowRevision: 1, originalId: "execution", metadata: {originalCreatedDate: ""}, state: state("SUCCESS")} as Execution

async function mountActions(overflow: boolean, extra: Record<string, unknown> = {}) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: "/:tenant/executions/:id/topology", name: "topology", component: {template: "<div />"}},
            {path: "/:tenant/executions", name: "executions/list", component: {template: "<div />"}},
        ],
    })
    await router.push({name: "topology", params: {tenant: "test-tenant", id: "execution"}})
    const taskRun = {id: "run-1", taskId: "loop", state: state("SUCCESS"), attempts: [{state: state("SUCCESS"), workerId: "worker-1"}]}
    const wrapper = i18nMount(TaskRunActions, {
        messages: {actions: "Actions", "more actions": "More actions", iterations: "Iterations", "download logs": "Download logs"},
        attachTo: document.body,
        props: {
            taskRun,
            taskRuns: [taskRun, {...taskRun, id: "run-2"}],
            execution,
            overflow,
            flow: {tasks: [{id: "loop", type: "io.kestra.plugin.core.flow.Loop"}]},
            ...extra,
        },
        global: {
            plugins: [createPinia(), router],
            stubs: {Metrics: true, Outputs: true, Restart: true, ChangeStatus: true, TaskEdit: true, WorkerInfo: true, Interrupt: true, NodeMenuItem: true},
        },
    })
    await wrapper.get(overflow ? "button[aria-label='More actions']" : "button[aria-label=Actions]").trigger("click")
    await flushPromises()
    return wrapper
}

const stubNames = ["Metrics", "Outputs", "Restart", "ChangeStatus", "TaskEdit", "WorkerInfo", "Interrupt"]

describe("TaskRunActions overflow", () => {
    it("shouldHideWhatTheTaskRunPopupAlreadyOffersInItsHeaderAndTabs", async () => {
        // When
        const wrapper = await mountActions(true)

        // Then
        stubNames.forEach((name) => expect(wrapper.findComponent({name}).exists(), name).toBe(false))
        expect(wrapper.findComponent({name: "TaskRunIterationSelector"}).exists()).toBe(false)
        wrapper.unmount()
    })

    it("shouldKeepTheLogsAndLoopActions", async () => {
        // When
        const wrapper = await mountActions(true)

        // Then
        const labels = wrapper.findAllComponents(KsDropdownItem).map((item) => item.text())
        expect(labels).toEqual(expect.arrayContaining(["Iterations", "Download logs", "copy logs", "delete logs"]))
        wrapper.unmount()
    })

    it("shouldAskToDismissItsHostBeforeLeavingForTheLoopIterations", async () => {
        // Given
        const wrapper = await mountActions(true)

        // When
        await wrapper.findAllComponents(KsDropdownItem).find((item) => item.text() === "Iterations")!.get("[role=\"menuitem\"]").trigger("click")

        // Then
        expect(wrapper.emitted("dismiss")).toHaveLength(1)
        wrapper.unmount()
    })

    it("shouldKeepEveryActionInTheDefaultMenu", async () => {
        // When
        const wrapper = await mountActions(false)

        // Then
        stubNames.forEach((name) => expect(wrapper.findComponent({name}).exists(), name).toBe(true))
        expect(wrapper.findComponent({name: "TaskRunIterationSelector"}).exists()).toBe(true)
        wrapper.unmount()
    })
})
