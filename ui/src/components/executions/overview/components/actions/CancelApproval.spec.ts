import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {defineComponent, type VNode} from "vue"

const cancelApproval = vi.hoisted(() => vi.fn())
const isReviewAllowed = vi.hoisted(() => vi.fn())
const confirm = vi.hoisted(() => vi.fn())
const success = vi.hoisted(() => vi.fn())

vi.mock("../../../../../stores/executions", () => ({
    useExecutionsStore: () => ({
        flow: {tasks: [{id: "approval", type: "io.kestra.plugin.core.flow.Approval"}]},
        isReviewAllowed,
        cancelApproval,
    }),
}))

vi.mock("../../../../../utils/toast", () => ({
    useToast: () => ({confirm, success}),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

import CancelApproval from "./CancelApproval.vue"
import {i18nMount} from "../../../../../../tests/unit/i18nMount"
import type {Execution} from "../../../../../stores/executions"

function mountAction() {
    return i18nMount(CancelApproval, {
        messages: {approval: {"cancel request": "Cancel request", "cancel confirm": "Cancel the approval of execution {id}?"}},
        props: {
            execution: {id: "e1", namespace: "ns", taskRunList: [{id: "tr1", taskId: "approval", state: {current: "PAUSED"}}]} as unknown as Execution,
        },
        global: {stubs: {NavBarAction: {template: "<button v-bind=\"$attrs\"><slot /></button>"}}},
    })
}

describe("CancelApproval", () => {
    beforeEach(() => {
        cancelApproval.mockReset().mockResolvedValue(undefined)
        isReviewAllowed.mockReset().mockResolvedValue(true)
        confirm.mockReset()
        success.mockReset()
    })

    it("cancels the paused approval request only once the user confirms", async () => {
        const wrapper = mountAction()
        await flushPromises()

        await wrapper.find("[data-test='cancel-approval-action']").trigger("click")

        expect(cancelApproval).not.toHaveBeenCalled()
        await confirm.mock.calls[0][1]()
        expect(cancelApproval).toHaveBeenCalledWith({id: "e1", taskRunId: "tr1"})
        expect(success).toHaveBeenCalledWith("approval.cancel done")
    })

    it("names the execution as code in the confirmation without markup in the translation", async () => {
        const wrapper = mountAction()
        await flushPromises()
        await wrapper.find("[data-test='cancel-approval-action']").trigger("click")

        const message = i18nMount(defineComponent({render: confirm.mock.calls[0][0] as () => VNode}), {
            messages: {approval: {"cancel confirm": "Cancel the approval of execution {id}?"}},
        })

        expect(message.text()).toBe("Cancel the approval of execution e1?")
        expect(message.find("code").text()).toBe("e1")
    })

    it("is hidden from a user who may not review the request", async () => {
        isReviewAllowed.mockResolvedValue(false)
        const wrapper = mountAction()
        await flushPromises()

        expect(wrapper.find("[data-test='cancel-approval-action']").exists()).toBe(false)
    })
})
