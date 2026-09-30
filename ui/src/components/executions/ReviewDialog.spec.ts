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
import type {Execution, InputMetaData} from "../../stores/executions"

const messages = {
    close: "Close",
    approval: {
        comment: "Comment",
        approve: "Approve",
        deny: "Deny",
    },
}

function mountDialog(decisions?: {approve?: string; deny?: string}, inputs?: InputMetaData[]) {
    return i18nMount(ReviewDialog, {
        messages,
        props: {
            modelValue: true,
            execution: {id: "e1", namespace: "ns"} as unknown as Execution,
            taskRun: {id: "tr1", taskId: "approval"},
            task: {id: "approval", type: "io.kestra.plugin.core.flow.Approval", decisions, inputs},
        },
        global: {
            stubs: {
                KsDialog: {props: ["dirty"], template: "<div data-test=\"review-dialog\" :data-dirty=\"dirty\"><slot name=\"header\" /><slot /><slot name=\"footer\" /></div>"},
                InputsForm: {
                    emits: ["update:modelValue", "ready"],
                    async mounted() {
                        await Promise.resolve()
                        this.$emit("update:modelValue", {confirmed: false})
                        this.$emit("ready")
                    },
                    template: "<div />",
                },
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

    it("labels each decision with the translated default unless the task names it", async () => {
        const wrapper = mountDialog({approve: "Ship it"})
        await flushPromises()

        expect(wrapper.find("[data-test='review-approve']").text()).toBe("Ship it")
        expect(wrapper.find("[data-test='review-deny']").text()).toBe("Deny")
    })

    it("is not dirty once the inputs form has written its defaults", async () => {
        const wrapper = mountDialog(undefined, [{id: "confirmed", type: "BOOL"}])
        await flushPromises()

        expect(wrapper.find("[data-test='review-dialog']").attributes("data-dirty")).toBe("false")
    })
})
