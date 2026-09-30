import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"

const validateReview = vi.hoisted(() => vi.fn())
const review = vi.hoisted(() => vi.fn())

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({validateReview, review}),
}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({error: vi.fn(), success: vi.fn()}),
}))

import ReviewDialog from "./ReviewDialog.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Execution} from "../../stores/executions"

const messages = {
    close: "Close",
    approval: {
        comment: "Comment",
        approve: "Approve",
        deny: "Deny",
    },
}

function mountDialog() {
    return i18nMount(ReviewDialog, {
        messages,
        props: {
            modelValue: true,
            execution: {id: "e1", namespace: "ns"} as unknown as Execution,
            taskRun: {id: "tr1", taskId: "approval"},
            task: {id: "approval", type: "io.kestra.plugin.core.flow.Approval"},
        },
        global: {
            stubs: {
                KsDialog: {template: "<div><slot name=\"header\" /><slot /><slot name=\"footer\" /></div>"},
                KsForm: {methods: {validate: (callback: (valid: boolean) => void) => callback(true)}, template: "<form><slot /></form>"},
                KsFormItem: {template: "<div><slot /></div>"},
                KsInput: {props: ["modelValue"], emits: ["update:modelValue"], template: "<textarea data-test=\"review-comment\" :value=\"modelValue\" @input=\"$emit('update:modelValue', $event.target.value)\" />"},
                KsText: {template: "<span><slot /></span>"},
                KsButton: {template: "<button><slot /></button>"},
            },
        },
    })
}

describe("ReviewDialog", () => {
    beforeEach(() => {
        validateReview.mockReset().mockResolvedValue({})
        review.mockReset().mockResolvedValue(undefined)
    })

    it("sends the decision and the comment for the task run being reviewed", async () => {
        const wrapper = mountDialog()
        await flushPromises()

        await wrapper.find("[data-test='review-comment']").setValue("ship it")
        await wrapper.find("[data-test='review-approve']").trigger("click")
        await flushPromises()

        expect(review).toHaveBeenCalledWith(expect.objectContaining({id: "e1", taskRunId: "tr1", decision: "APPROVE"}))
        expect((review.mock.calls[0][0].formData as FormData).get("kestra_review_comment")).toBe("ship it")
    })
})
