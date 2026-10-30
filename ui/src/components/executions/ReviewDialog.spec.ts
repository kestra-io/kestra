import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"

const validateReview = vi.hoisted(() => vi.fn())
const review = vi.hoisted(() => vi.fn())
const error = vi.hoisted(() => vi.fn())
const loadTaskRunOutputs = vi.hoisted(() => vi.fn())
const inputsForm = vi.hoisted(() => ({ready: () => {}, validation: undefined as undefined | ((payload: unknown) => void)}))

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({validateReview, review}),
}))

vi.mock("../../composables/useTaskRunOutputs", () => ({loadTaskRunOutputs}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({error, success: vi.fn()}),
}))

import ReviewDialog from "./ReviewDialog.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Execution, InputMetaData} from "../../stores/executions"

const messages = {
    close: "Close",
    approval: {
        title: "Review {task} of execution {id}",
        comment: "Comment",
        approve: "Approve",
        deny: "Deny",
    },
}

function mountDialog(decisions?: {approve?: string; deny?: string}, inputs?: InputMetaData[], commentRequired?: string) {
    return i18nMount(ReviewDialog, {
        messages,
        props: {
            modelValue: true,
            execution: {id: "e1", namespace: "ns"} as unknown as Execution,
            taskRun: {id: "tr1", taskId: "approval"},
            task: {id: "approval", type: "io.kestra.plugin.core.flow.Approval", decisions, inputs, commentRequired},
        },
        global: {
            stubs: {
                KsDialog: {props: ["dirty"], template: "<div data-test=\"review-dialog\" :data-dirty=\"dirty\"><slot name=\"header\" /><slot /><slot name=\"footer\" /></div>"},
                InputsForm: {
                    emits: ["update:modelValue", "ready", "validation"],
                    mounted() {
                        inputsForm.ready = () => {
                            this.$emit("update:modelValue", {confirmed: false})
                            this.$emit("ready")
                        }
                        inputsForm.validation = (payload: unknown) => this.$emit("validation", payload)
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
        error.mockReset()
        loadTaskRunOutputs.mockReset().mockResolvedValue({})
    })

    it("names the approval task and the execution in its header", async () => {
        const wrapper = mountDialog()
        await flushPromises()

        expect(wrapper.text()).toContain("Review approval of execution e1")
    })

    it("shows the rendered title of the approval request", async () => {
        loadTaskRunOutputs.mockResolvedValue({title: "Deploy release 1.4 to prod"})
        const wrapper = mountDialog()
        await flushPromises()

        expect(wrapper.find("[data-test='review-title']").text()).toBe("Deploy release 1.4 to prod")
    })

    it("validates the inputs against this approval's task run, not against whichever task resume would pick", async () => {
        mountDialog(undefined, [{id: "confirmed", type: "BOOL"}])
        await flushPromises()
        const callback = vi.fn()

        inputsForm.validation?.({formData: new FormData(), inputsMetaData: [], callback})
        await flushPromises()

        expect(validateReview).toHaveBeenCalledWith(expect.objectContaining({id: "e1", taskRunId: "tr1", decision: "APPROVE"}))
        expect(callback).toHaveBeenCalledWith({})
    })

    it("keeps a comment typed before the inputs form is ready as an unsaved change", async () => {
        const wrapper = mountDialog(undefined, [{id: "confirmed", type: "BOOL"}])
        await flushPromises()

        await wrapper.find("[data-test='review-comment']").setValue("half written")
        inputsForm.ready()
        await flushPromises()

        expect(wrapper.find("[data-test='review-dialog']").attributes("data-dirty")).toBe("true")
    })

    it("requires a comment as the rendered commentRequired says when the task property is an expression", async () => {
        loadTaskRunOutputs.mockResolvedValue({commentRequired: "ON_DENY"})
        const wrapper = mountDialog(undefined, undefined, "{{ inputs.strict }}")
        await flushPromises()

        await wrapper.find("[data-test='review-deny']").trigger("click")
        await flushPromises()

        expect(loadTaskRunOutputs).toHaveBeenCalledWith("e1", "tr1")
        expect(wrapper.find("[data-test='review-comment-hint']").text()).toBe("approval.comment hint.on deny")
        expect(error).toHaveBeenCalledWith("approval.comment required")
        expect(review).not.toHaveBeenCalled()
    })

    it("sends the comment without its surrounding whitespace", async () => {
        const wrapper = mountDialog()
        await flushPromises()

        await wrapper.find("[data-test='review-comment']").setValue("  ship it  ")
        await wrapper.find("[data-test='review-approve']").trigger("click")
        await flushPromises()

        expect((review.mock.calls[0][0].formData as FormData).get("kestra_review_comment")).toBe("ship it")
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
        inputsForm.ready()
        await flushPromises()

        expect(wrapper.find("[data-test='review-dialog']").attributes("data-dirty")).toBe("false")
    })

    it("refuses a decision that needs a comment without sending it", async () => {
        const wrapper = mountDialog(undefined, undefined, "ON_DENY")
        await flushPromises()

        await wrapper.find("[data-test='review-deny']").trigger("click")
        await flushPromises()

        expect(error).toHaveBeenCalledWith("approval.comment required")
        expect(validateReview).not.toHaveBeenCalled()
        expect(review).not.toHaveBeenCalled()
    })

    it("does not send a decision the server rejects the inputs of", async () => {
        validateReview.mockResolvedValue({inputs: [{errors: [{message: "too small"}]}]})
        const wrapper = mountDialog()
        await flushPromises()

        await wrapper.find("[data-test='review-approve']").trigger("click")
        await flushPromises()

        expect(error).toHaveBeenCalledWith("approval.inputs invalid")
        expect(review).not.toHaveBeenCalled()
    })
})
