import {describe, expect, it} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createI18n} from "vue-i18n"
import {createMemoryHistory, createRouter} from "vue-router"
import {KsButton, KsDropdown, KsDropdownItem, KsDropdownMenu} from "@kestra-io/design-system"
import type {State, StateType} from "@kestra-io/kestra-sdk"
import type {Execution} from "../../stores/executions"
import TaskRunActions from "./TaskRunActions.vue"

function state(current: StateType): State {
    return {current, histories: []}
}

function executionProps(id: string, namespace: string, flowId: string, executionState: State = state("RUNNING")): Execution {
    return {id, namespace, flowId, flowRevision: 1, originalId: id, metadata: {originalCreatedDate: ""}, state: executionState}
}

async function mountActions(type: string, grouped = false, executionState: State = state("RUNNING")) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: "/:tenant/executions/:id/topology", name: "topology", component: {template: "<div />"}},
            {path: "/:tenant/executions", name: "executions/list", component: {template: "<div />"}},
        ],
    })
    await router.push({name: "topology", params: {tenant: "test-tenant", id: "parent-execution"}})
    const taskRun = {id: "task-run", taskId: "loop", state: state("RUNNING")}
    const wrapper = mount(TaskRunActions, {
        attachTo: document.body,
        props: {
            taskRun,
            taskRuns: grouped ? [taskRun, {...taskRun, id: "another-task-run"}] : undefined,
            execution: executionProps("parent-execution", "tests", "loop-flow", executionState),
            flow: {tasks: [{id: "parallel", type: "io.kestra.plugin.core.flow.Parallel", tasks: [{id: "loop", type}]}]},
        },
        global: {
            plugins: [createPinia(), router, createI18n({legacy: false, locale: "en", messages: {en: {actions: "Actions", iterations: "Iterations"}}})],
            components: {KsButton, KsDropdown, KsDropdownItem, KsDropdownMenu},
            stubs: {
                Metrics: true,
                Outputs: true,
                Restart: true,
                ChangeStatus: true,
                TaskEdit: true,
                WorkerInfo: true,
                NodeMenuItem: true,
            },
        },
    })
    await wrapper.get("button[aria-label=Actions]").trigger("click")
    await flushPromises()
    return {wrapper, router}
}

describe("TaskRunActions", () => {
    it.each([false, true])("shouldOpenAllIterationsWhenNestedLoopHasNoOutputs (grouped: %s)", async grouped => {
        const {wrapper, router} = await mountActions("io.kestra.plugin.core.flow.Loop", grouped)
        const iterations = wrapper.findAllComponents(KsDropdownItem).find(item => item.text() === "Iterations")

        expect(iterations).toBeDefined()
        await iterations!.get("[role=\"menuitem\"]").trigger("click")
        await flushPromises()

        expect(router.currentRoute.value.name).toBe("executions/list")
        expect(router.currentRoute.value.params.tenant).toBe("test-tenant")
        expect(router.currentRoute.value.query).toEqual({
            "filters[parentId][EQUALS]": "parent-execution",
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": "loop",
        })
    })

    it("shouldBoundIterationsToTheParentWindowSoTheDefaultTimeRangeIsNotApplied", async () => {
        const {wrapper, router} = await mountActions("io.kestra.plugin.core.flow.Loop", false, {
            ...state("SUCCESS"),
            startDate: "2026-10-05T08:00:00Z",
            endDate: "2026-10-05T08:03:00Z",
        })
        const iterations = wrapper.findAllComponents(KsDropdownItem).find(item => item.text() === "Iterations")

        await iterations!.get("[role=\"menuitem\"]").trigger("click")
        await flushPromises()

        expect(router.currentRoute.value.query).toEqual({
            "filters[parentId][EQUALS]": "parent-execution",
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": "loop",
            "filters[startDate][GREATER_THAN_OR_EQUAL_TO]": "2026-10-05T08:00:00.000Z",
            "filters[endDate][LESS_THAN_OR_EQUAL_TO]": "2026-10-05T08:03:00.000Z",
        })
    })

    it("shouldHideIterationsWhenTaskIsNotALoop", async () => {
        const {wrapper} = await mountActions("io.kestra.plugin.core.log.Log")

        expect(wrapper.findAllComponents(KsDropdownItem).map(item => item.text())).not.toContain("Iterations")
    })

    it("shouldHideIterationsWhenFlowIsUnavailable", async () => {
        const {wrapper} = await mountActions("io.kestra.plugin.core.flow.Loop")

        expect(wrapper.findAllComponents(KsDropdownItem).map(item => item.text())).toContain("Iterations")
        await wrapper.setProps({flow: undefined})
        expect(wrapper.findAllComponents(KsDropdownItem).map(item => item.text())).not.toContain("Iterations")
    })

    it("shouldUseTopologyNodeTypeWhenLoopIsInsideExpandedSubflow", async () => {
        const {wrapper, router} = await mountActions("io.kestra.plugin.core.log.Log")
        await wrapper.setProps({
            taskType: "io.kestra.plugin.core.flow.Loop",
            taskRun: {id: "subflow-task-run", taskId: "subflow-loop", state: state("RUNNING")},
            execution: executionProps("subflow-execution", "tests", "child-flow"),
        })
        const iterations = wrapper.findAllComponents(KsDropdownItem).find(item => item.text() === "Iterations")

        expect(iterations).toBeDefined()
        await iterations!.get("[role=\"menuitem\"]").trigger("click")
        await flushPromises()

        expect(router.currentRoute.value.query).toEqual({
            "filters[parentId][EQUALS]": "subflow-execution",
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": "subflow-loop",
        })
    })
})
