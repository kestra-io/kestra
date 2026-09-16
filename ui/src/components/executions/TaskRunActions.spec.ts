import {describe, expect, it} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia} from "pinia"
import {createI18n} from "vue-i18n"
import {createMemoryHistory, createRouter} from "vue-router"
import {KsButton, KsDropdown, KsDropdownItem, KsDropdownMenu} from "@kestra-io/design-system"
import TaskRunActions from "./TaskRunActions.vue"

async function mountActions(type: string, grouped = false) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: "/:tenant/executions/:id/topology", name: "topology", component: {template: "<div />"}},
            {path: "/:tenant/executions", name: "executions/list", component: {template: "<div />"}},
        ],
    })
    await router.push({name: "topology", params: {tenant: "test-tenant", id: "parent-execution"}})
    const taskRun = {id: "task-run", taskId: "loop", state: {current: "RUNNING"}}
    const wrapper = mount(TaskRunActions, {
        attachTo: document.body,
        props: {
            taskRun,
            taskRuns: grouped ? [taskRun, {...taskRun, id: "another-task-run"}] : undefined,
            execution: {id: "parent-execution", namespace: "tests", flowId: "loop-flow", state: {current: "RUNNING"}},
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
    it.each([false, true])("should open all iterations for a nested Loop without outputs (grouped: %s)", async grouped => {
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

    it("should hide iterations for ordinary tasks and while the flow is unavailable", async () => {
        const {wrapper} = await mountActions("io.kestra.plugin.core.log.Log")

        expect(wrapper.findAllComponents(KsDropdownItem).map(item => item.text())).not.toContain("Iterations")
        await wrapper.setProps({flow: undefined})
        expect(wrapper.findAllComponents(KsDropdownItem).map(item => item.text())).not.toContain("Iterations")
    })

    it("should use the topology node type for Loops inside an expanded subflow", async () => {
        const {wrapper, router} = await mountActions("io.kestra.plugin.core.log.Log")
        await wrapper.setProps({taskType: "io.kestra.plugin.core.flow.Loop"})
        const iterations = wrapper.findAllComponents(KsDropdownItem).find(item => item.text() === "Iterations")

        expect(iterations).toBeDefined()
        await iterations!.get("[role=\"menuitem\"]").trigger("click")
        await flushPromises()

        expect(router.currentRoute.value.query["filters[parentId][EQUALS]"]).toBe("parent-execution")
    })
})
