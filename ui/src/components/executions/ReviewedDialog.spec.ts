import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"

const loadTaskRunOutputs = vi.hoisted(() => vi.fn())

vi.mock("../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs}))

import ReviewedDialog from "./ReviewedDialog.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Execution} from "../../stores/executions"

function mountDialog(taskRunState = "RUNNING") {
    return i18nMount(ReviewedDialog, {
        props: {
            modelValue: true,
            execution: {id: "e1", namespace: "ns", taskRunList: [{id: "tr1", taskId: "approval", state: {current: taskRunState}}]} as unknown as Execution,
            taskRun: {id: "tr1", taskId: "approval"},
        },
        global: {
            stubs: {
                KsDialog: {template: "<div><slot name=\"header\" /><slot /></div>"},
                KsJsonTree: {props: ["value"], template: "<pre>{{ JSON.stringify(value) }}</pre>"},
            },
        },
    })
}

describe("ReviewedDialog", () => {
    beforeEach(() => {
        loadTaskRunOutputs.mockReset()
    })

    it("shows the decision, who took it, the comment and the inputs of a request denied with the default CANCEL behavior", async () => {
        loadTaskRunOutputs.mockResolvedValue({decision: "DENIED", by: "alice", on: "2026-10-27T10:00:00Z", comment: "too expensive", inputs: {budget: 12}})
        const wrapper = mountDialog("CANCELLED")
        await flushPromises()

        expect(loadTaskRunOutputs).toHaveBeenCalledWith("e1", "tr1")
        expect(wrapper.find("[data-test='reviewed-decision']").text()).toBe("approval.reviewed.decisions.denied")
        expect(wrapper.find("[data-test='reviewed-by']").text()).toBe("alice")
        expect(wrapper.find("[data-test='reviewed-on']").exists()).toBe(true)
        expect(wrapper.find("[data-test='reviewed-comment']").text()).toBe("too expensive")
        expect(wrapper.find("[data-test='reviewed-inputs']").text()).toContain("budget")
        expect(wrapper.text()).not.toContain("approval.reviewed.cancelled by")
    })

    it("shows who cancelled a request and when", async () => {
        loadTaskRunOutputs.mockResolvedValue({by: "bob", on: "2026-10-27T10:00:00Z"})
        const wrapper = mountDialog("CANCELLED")
        await flushPromises()

        expect(wrapper.find("[data-test='reviewed-decision']").text()).toBe("approval.reviewed.decisions.cancelled")
        expect(wrapper.find("[data-test='reviewed-by']").text()).toBe("bob")
        expect(wrapper.text()).toContain("approval.reviewed.cancelled by")
        expect(wrapper.find("[data-test='reviewed-on']").exists()).toBe(true)
    })

    it("shows that a request was killed", async () => {
        loadTaskRunOutputs.mockResolvedValue({})
        const wrapper = mountDialog("KILLED")
        await flushPromises()

        expect(wrapper.find("[data-test='reviewed-decision']").text()).toBe("approval.reviewed.decisions.killed")
    })

    it("only explains that the request was reviewed to a viewer who cannot read its outputs", async () => {
        loadTaskRunOutputs.mockRejectedValue({response: {status: 403}})
        const wrapper = mountDialog()
        await flushPromises()

        expect(wrapper.find("[data-test='reviewed-explanation']").exists()).toBe(true)
        expect(wrapper.find("[data-test='reviewed-decision']").exists()).toBe(false)
    })
})
