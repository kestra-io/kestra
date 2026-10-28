import {describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"
import {useExecutionsStore} from "../../stores/executions"
import {useFlowStore} from "../../stores/flow"
import TaskRunEditedDefinition from "./TaskRunEditedDefinition.vue"

const source = "id: flow1\nnamespace: tests\ntasks:\n  - id: adjust\n    type: io.kestra.plugin.core.log.Log\n    message: before\n"

async function openDialog(loadTaskRunEditedDefinition: () => Promise<string>) {
    setActivePinia(createPinia())
    const executionsStore = useExecutionsStore()
    const flowStore = useFlowStore()
    flowStore.loadFlow = vi.fn().mockResolvedValue({source}) as typeof flowStore.loadFlow
    executionsStore.loadTaskRunEditedDefinition = vi.fn(loadTaskRunEditedDefinition) as typeof executionsStore.loadTaskRunEditedDefinition

    const wrapper = i18nMount(TaskRunEditedDefinition, {
        locales: en,
        props: {
            executionId: "exec1",
            namespace: "tests",
            flowId: "flow1",
            revision: 1,
            taskRunId: "tr1",
            taskId: "adjust",
            edit: {id: "e1", editedBy: "jane", editedAt: "2026-10-27T10:00:00Z"},
        },
        global: {
            stubs: {
                KsDialog: {template: "<div><slot /></div>"},
                DiffView: {props: ["oldValue", "newValue"], template: "<pre data-test='diff'>{{ oldValue }}---{{ newValue }}</pre>"},
            },
        },
    })
    await wrapper.find("[data-test='task-run-edited-definition']").trigger("click")
    await flushPromises()
    return wrapper
}

describe("TaskRunEditedDefinition", () => {
    it("diffs the definition of the revision against the edited one", async () => {
        const wrapper = await openDialog(() => Promise.resolve("id: adjust\ntype: io.kestra.plugin.core.log.Log\nmessage: after\n"))

        expect(wrapper.find("[data-test='diff']").text()).toContain("message: before")
        expect(wrapper.find("[data-test='diff']").text()).toContain("message: after")
    })

    it("explains why the edit is hidden when the flow cannot be viewed", async () => {
        const forbidden = Object.assign(new Error("Forbidden"), {problem: {type: "about:blank", status: 403, title: "Forbidden"}})
        const wrapper = await openDialog(() => Promise.reject(forbidden))

        expect(wrapper.find("[data-test='diff']").exists()).toBe(false)
        expect(wrapper.find("[data-test='task-run-edited-definition-hidden']").exists()).toBe(true)
    })
})
