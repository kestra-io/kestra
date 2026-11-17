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
            const onFieldChange = (fieldId: string) => {
                const prop = fieldId.includes(".") ? [fieldId] : fieldId
                formRef.value?.clearValidate(prop)
            }
            return {formRef, inputs, onFieldChange, inputsList, flow}
        },
        template: `
            <KsForm ref="formRef" :model="inputs" labelPosition="top">
                <InputsForm
                    :flow="flow"
                    :initialInputs="inputsList"
                    v-model="inputs"
                    @change="onFieldChange"
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
        expect(wrapper.find(".kel-form-item__error, .el-form-item__error").exists()).toBe(false)

        // 2. Submit with required reason empty: form.validate() triggers validation
        let isValid: boolean | undefined
        await form?.validate((valid: boolean) => {
            isValid = valid
        })
        await flushPromises()
        await new Promise(r => setTimeout(r, 150))
        await flushPromises()

        // 3. Verify 'reason is required' appears
        expect(isValid).toBe(false)
        const errorEl = wrapper.find(".kel-form-item__error, .el-form-item__error")
        expect(errorEl.exists()).toBe(true)
        expect(errorEl.text()).toContain("reason")
        expect(errorEl.text()).toContain("is required")

        // 4. Type a valid value into the KsEditor
        const textarea = wrapper.find("textarea")
        expect(textarea.exists()).toBe(true)
        textarea.element.value = "deploying urgent hotfix"
        await textarea.trigger("input")

        // 5. Click elsewhere / blur
        await textarea.trigger("blur")
        await flushPromises()
        await new Promise(r => setTimeout(r, 150))
        await flushPromises()

        // 6. Verify the error disappears without another form submission
        expect(wrapper.find(".kel-form-item__error, .el-form-item__error").exists()).toBe(false)
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
        await new Promise(r => setTimeout(r, 150))
        await flushPromises()

        // Both fields have errors initially
        const errorsBefore = wrapper.findAll(".kel-form-item__error, .el-form-item__error")
        expect(errorsBefore).toHaveLength(2)

        // Type into dotted field approval.approver only
        const textareas = wrapper.findAll("textarea")
        expect(textareas).toHaveLength(2)
        textareas[0].element.value = "admin-user"
        await textareas[0].trigger("input")
        await textareas[0].trigger("blur")
        await flushPromises()
        await new Promise(r => setTimeout(r, 150))
        await flushPromises()

        // approval.approver error is cleared, reason error remains
        const errorsAfter = wrapper.findAll(".kel-form-item__error, .el-form-item__error")
        expect(errorsAfter).toHaveLength(1)
        expect(errorsAfter[0].text()).toContain("reason")
        expect(errorsAfter[0].text()).toContain("is required")
    })
})
