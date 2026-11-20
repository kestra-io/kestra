import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {defineComponent, ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem, {KsForm, type FormInstance} from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import InputsForm from "./InputsForm.vue"
import {useExecutionsStore, type InputMetaData} from "../../stores/executions"
import type {Flow} from "../../stores/flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal() as Record<string, unknown>
    return {
        ...actual,
        KsMarkdown: {name: "KsMarkdown", props: ["content"], template: "<div>{{ content }}</div>"},
        KsEditor: defineComponent({
            name: "KsEditor",
            props: ["modelValue", "dataTestid"],
            emits: ["update:modelValue", "confirm"],
            template: "<div class=\"stub-editor\"><textarea :value=\"modelValue\" @input=\"$emit('update:modelValue', $event.target.value)\" @blur=\"$emit('update:modelValue', $event.target.value)\" /></div>",
        }),
    }
})

const flow = {namespace: "io.kestra.tests", id: "my_flow"} as Flow

function createTestHarness(inputsList: InputMetaData[]) {
    return defineComponent({
        components: {KsForm, InputsForm},
        setup() {
            const formRef = ref<FormInstance | null>(null)
            const inputs = ref<Record<string, unknown>>({})
            const onClearValidation = (fieldId: string) => {
                formRef.value?.clearValidate(fieldId)
            }
            return {formRef, inputs, onClearValidation, inputsList, flow}
        },
        template: `
            <KsForm ref="formRef" :model="inputs" labelPosition="top">
                <InputsForm
                    :flow="flow"
                    :initialInputs="inputsList"
                    v-model="inputs"
                    @clear-validation="onClearValidation"
                />
            </KsForm>
        `,
    })
}

describe("InputsForm real form validation lifecycle (#22065)", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    test("exercises the exact reproduction sequence: empty submit, error appears, valid KsEditor entry, blur, error disappears", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({
            checks: [],
            inputs: [
                {
                    enabled: true,
                    isDefault: false,
                    input: {id: "reason", type: "STRING", required: true},
                    errors: [],
                },
            ],
        })

        const harness = createTestHarness([
            {id: "reason", type: "STRING", required: true},
        ])

        const wrapper = i18nMount(harness, {
            global: {
                plugins: [KestraDesignSystem],
            },
        })
        await flushPromises()

        const form = wrapper.vm.formRef
        expect(form).toBeDefined()

        // 1. Initial state: no error is shown
        expect(wrapper.text()).not.toContain("reason is required")

        // 2. Submit with required reason empty: form.validate() triggers validation
        let isValid: boolean | undefined
        await form?.validate((valid: boolean) => {
            isValid = valid
        })
        await flushPromises()

        // 3. Verify 'reason is required' appears
        expect(isValid).toBe(false)
        await vi.waitFor(() => {
            expect(wrapper.text()).toContain("reason is required")
        })

        // 4. Type a valid value into the KsEditor
        const textarea = wrapper.find("textarea")
        expect(textarea.exists()).toBe(true)
        textarea.element.value = "deploying urgent hotfix"
        await textarea.trigger("input")

        // 5. Click elsewhere / blur
        await textarea.trigger("blur")
        await flushPromises()

        // 6. Verify the error disappears without another form submission
        await vi.waitFor(() => {
            expect(wrapper.text()).not.toContain("reason is required")
        })
    })

    test("clears error on dotted field paths without clearing unrelated field errors", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({
            checks: [],
            inputs: [
                {enabled: true, isDefault: false, input: {id: "approval.approver", type: "STRING", required: true}, errors: []},
                {enabled: true, isDefault: false, input: {id: "reason", type: "STRING", required: true}, errors: []},
            ],
        })

        const harness = createTestHarness([
            {id: "approval.approver", type: "STRING", required: true},
            {id: "reason", type: "STRING", required: true},
        ])

        const wrapper = i18nMount(harness, {
            global: {
                plugins: [KestraDesignSystem],
            },
        })
        await flushPromises()

        const form = wrapper.vm.formRef
        await form?.validate(() => {})
        await flushPromises()

        // Both fields have required error messages initially
        await vi.waitFor(() => {
            expect(wrapper.text().match(/is required/g)).toHaveLength(2)
            expect(wrapper.text()).toContain("approval.approver is required")
            expect(wrapper.text()).toContain("reason is required")
        })

        // Type into dotted field approval.approver only
        const textareas = wrapper.findAll("textarea")
        expect(textareas).toHaveLength(2)
        textareas[0].element.value = "admin-user"
        await textareas[0].trigger("input")
        await textareas[0].trigger("blur")
        await flushPromises()

        // approval.approver error is cleared, only reason error remains
        await vi.waitFor(() => {
            expect(wrapper.text().match(/is required/g)).toHaveLength(1)
            expect(wrapper.text()).not.toContain("approval.approver is required")
            expect(wrapper.text()).toContain("reason is required")
        })
    })

    test("preserves existing server-side validation error when entering another invalid value", async () => {
        const store = useExecutionsStore()
        const errorMessage = "Invalid value: must match pattern ^[A-Z]+$"
        store.validateExecution = vi.fn().mockResolvedValue({
            checks: [],
            inputs: [
                {
                    enabled: true,
                    isDefault: false,
                    input: {id: "code", type: "STRING", required: true},
                    errors: [{message: errorMessage, renderError: false}],
                },
            ],
        })

        const harness = createTestHarness([
            {id: "code", type: "STRING", required: true},
        ])

        const wrapper = i18nMount(harness, {
            global: {
                plugins: [KestraDesignSystem],
            },
        })
        await flushPromises()

        // 1. Initial state before entering a value: server error is not visible yet (normal delay)
        expect(wrapper.text()).not.toContain(errorMessage)

        // 2. Enter an invalid value
        const textarea = wrapper.find("textarea")
        expect(textarea.exists()).toBe(true)
        textarea.element.value = "invalid-1"
        await textarea.trigger("input")
        await textarea.trigger("blur")
        await flushPromises()

        // 3. Wait for the server validation error to become visible after the normal validation delay
        await vi.waitFor(() => {
            expect(wrapper.text()).toContain(errorMessage)
        }, {timeout: 3000})

        // 4. Enter another invalid value
        textarea.element.value = "invalid-2"
        await textarea.trigger("input")
        await textarea.trigger("blur")
        await flushPromises()

        // 5. Verify that the existing server-side error is not cleared (clearValidation is guarded)
        expect(wrapper.text()).toContain(errorMessage)

        // 6. Let revalidation return the same error message
        await flushPromises()

        // 7. Verify the rendered error remains visible
        await vi.waitFor(() => {
            expect(wrapper.text()).toContain(errorMessage)
        })
    })
})
