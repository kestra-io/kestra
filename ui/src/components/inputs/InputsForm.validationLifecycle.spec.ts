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

        expect(wrapper.text()).not.toContain("reason is required")

        let isValid: boolean | undefined
        await form?.validate((valid: boolean) => {
            isValid = valid
        })
        await flushPromises()

        expect(isValid).toBe(false)
        await vi.waitFor(() => {
            expect(wrapper.text()).toContain("reason is required")
        })

        const textarea = wrapper.find("textarea")
        expect(textarea.exists()).toBe(true)
        textarea.element.value = "deploying urgent hotfix"
        await textarea.trigger("input")

        await textarea.trigger("blur")
        await flushPromises()

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

        await vi.waitFor(() => {
            expect(wrapper.text().match(/is required/g)).toHaveLength(2)
            expect(wrapper.text()).toContain("approval.approver is required")
            expect(wrapper.text()).toContain("reason is required")
        })

        const textareas = wrapper.findAll("textarea")
        expect(textareas).toHaveLength(2)
        textareas[0].element.value = "admin-user"
        await textareas[0].trigger("input")
        await textareas[0].trigger("blur")
        await flushPromises()

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

        expect(wrapper.text()).not.toContain(errorMessage)

        const textarea = wrapper.find("textarea")
        expect(textarea.exists()).toBe(true)
        textarea.element.value = "invalid-1"
        await textarea.trigger("input")
        await textarea.trigger("blur")
        await flushPromises()

        await vi.waitFor(() => {
            expect(wrapper.text()).toContain(errorMessage)
        }, {timeout: 3000})

        textarea.element.value = "invalid-2"
        await textarea.trigger("input")
        await textarea.trigger("blur")
        await flushPromises()

        expect(wrapper.text()).toContain(errorMessage)

        await flushPromises()

        await vi.waitFor(() => {
            expect(wrapper.text()).toContain(errorMessage)
        })
    })
})
