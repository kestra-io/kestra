import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {defineComponent, h, ref} from "vue"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import ElementPlus, {ElForm, ElFormItem, ElInput, ElInputNumber, ElSelect} from "element-plus"
import InputsForm from "../../../../src/components/inputs/InputsForm.vue"
import DurationPicker from "../../../../src/components/inputs/DurationPicker.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"
import en from "../../../../src/translations/en.json"

vi.mock("vue-router", async (importOriginal) => {
    const actual = await importOriginal<typeof import("vue-router")>()
    return {
        ...actual,
        useRoute: () => ({query: {}, params: {}, name: "flow"}),
        useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
    }
})

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: en as any,
    fallbackWarn: false,
    missingWarn: false,
    globalInjection: true,
})

const flow = {namespace: "io.kestra.tests", id: "my_flow"} as any

// These tests assert the *gating* — which controls are allowed to self-validate, and what rule each
// el-form-item is handed — rather than the rendered error text. Element Plus's async validation never
// settles under jsdom (a bare el-form + el-form-item + el-select stays stuck on `is-validating`, with
// no Kestra code involved), so asserting on `.el-form-item__error` would pass vacuously in every case.
function mountForm(initialInputs: any[]) {
    const Harness = defineComponent({
        setup(_props, {expose}) {
            const inputs = ref<Record<string, any>>({})
            const inputsFormRef = ref<any>(null)
            expose({inputs, inputsFormRef})
            // InputsForm renders bare el-form-items; the surrounding el-form (and its :model) lives in
            // the parent (FlowRun / Resume), so mount it the same way.
            return () => h(ElForm, {model: inputs.value}, () => [
                h(InputsForm, {
                    ref: inputsFormRef,
                    flow,
                    initialInputs,
                    modelValue: inputs.value,
                    "onUpdate:modelValue": (value: Record<string, any>) => {inputs.value = value},
                }),
            ])
        },
    })

    return mount(Harness, {
        global: {
            plugins: [i18n, ElementPlus],
            stubs: {Editor: true, Markdown: true},
        },
        attachTo: document.body,
    })
}

// The harness exposes the wrapped InputsForm so tests can read its defineExpose'd state.
const vm = (wrapper: ReturnType<typeof mountForm>) =>
    wrapper.vm as unknown as {inputs: Record<string, any>; inputsFormRef: any}

// Blurring a field is what marks it interacted — the el-form-item listens for the bubbling focusout.
const blur = async (wrapper: ReturnType<typeof mountForm>, index = 0) => {
    await wrapper.findAll(".el-form-item")[index].trigger("focusout")
    await flushPromises()
}

const rulesFor = (wrapper: ReturnType<typeof mountForm>, index = 0) =>
    wrapper.findAllComponents(ElFormItem)[index].props("rules") as any

describe("InputsForm gates validation until interaction", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    // The reported regression (#17651): a per-field `input.errors` list rendered the backend's errors
    // with no gate at all, so the form opened already red — on required AND optional fields alike.
    // #5232 gated it, #15177 dropped the gate. The same errors already render through the gated
    // `:error="inputError(id)"`, so the list is redundant; if it ever comes back, this fails.
    test("renders no backend error on load, even when validate returns one for every field", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "region", type: "SELECT", values: ["us", "eu"]},
                errors: [{message: "Missing required input:region"}]},
            {enabled: true, isDefault: false, input: {id: "comment", type: "STRING", required: false},
                errors: [{message: "Missing required input:comment"}]},
        ]}})

        const wrapper = mountForm([
            {id: "region", type: "SELECT", values: ["us", "eu"]},
            {id: "comment", type: "STRING", required: false},
        ])
        await flushPromises()

        expect(wrapper.text()).not.toContain("Missing required input")
        // the gated channel still holds them, ready for submit / once the field is validated
        expect(vm(wrapper).inputsFormRef.inputError("region")).toBeUndefined()
        vm(wrapper).inputsFormRef.inputsValidated.add("region")
        expect(vm(wrapper).inputsFormRef.inputError("region")).toContain("Missing required")
    })

    // Cause #2: el-select & friends call formItem.validate() from their own internal watchers, with no
    // user interaction — a value arriving from the server is enough. validateEvent is the only thing
    // that suppresses that, so it must start false and flip once the field has been visited.
    test("starts every control with validateEvent off, and turns it on once the field is blurred", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: true, value: "us", input: {id: "region", type: "SELECT", values: ["us", "eu"]}},
        ]}})

        const wrapper = mountForm([{id: "region", type: "SELECT", values: ["us", "eu"], defaults: "us"}])
        await flushPromises()

        // a default landed from the server — still not interaction
        expect(vm(wrapper).inputsFormRef.inputsValues.region).toBe("us")
        expect(vm(wrapper).inputsFormRef.isInteracted("region")).toBe(false)
        expect(wrapper.findComponent(ElSelect).props("validateEvent")).toBe(false)

        await blur(wrapper)

        expect(vm(wrapper).inputsFormRef.isInteracted("region")).toBe(true)
        expect(wrapper.findComponent(ElSelect).props("validateEvent")).toBe(true)
    })

    test("gates each field independently — blurring one leaves the others untouched", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "region", type: "SELECT", values: ["us", "eu"]}},
            {enabled: true, isDefault: false, input: {id: "env", type: "SELECT", values: ["dev", "prod"]}},
        ]}})

        const wrapper = mountForm([
            {id: "region", type: "SELECT", values: ["us", "eu"]},
            {id: "env", type: "SELECT", values: ["dev", "prod"]},
        ])
        await flushPromises()
        await blur(wrapper, 0)

        expect(wrapper.findAllComponents(ElSelect).map(c => c.props("validateEvent"))).toEqual([true, false])
        expect(vm(wrapper).inputsFormRef.isInteracted("env")).toBe(false)
    })

    // DurationPicker holds seven el-input-numbers plus an el-input, all seeded by its own onMounted —
    // ungated they validate the surrounding el-form-item before the user has arrived.
    test("forwards the gate into DurationPicker's inner controls", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "timeout", type: "DURATION"}},
        ]}})

        const wrapper = mountForm([{id: "timeout", type: "DURATION"}])
        await flushPromises()

        const picker = wrapper.findComponent(DurationPicker)
        expect(picker.props("validateEvent")).toBe(false)
        // every inner Element Plus control, not just the wrapper
        expect(picker.findAllComponents(ElInputNumber)).toHaveLength(7)
        expect(picker.findAllComponents(ElInputNumber).every(c => c.props("validateEvent") === false)).toBe(true)
        expect(picker.findComponent(ElInput).props("validateEvent")).toBe(false)

        await blur(wrapper)

        expect(picker.props("validateEvent")).toBe(true)
        expect(picker.findAllComponents(ElInputNumber).every(c => c.props("validateEvent") === true)).toBe(true)
    })

    // Types with no el-* control of their own (STRING/JSON/YAML render Editor) used to get no rule at
    // all, leaving el-form-item to derive an implicit `{required: true}` from its own `required` prop —
    // whose message is async-validator's hardcoded English "%s is required" built from the raw input id.
    test("hands a translated, displayName-based required rule to types without an explicit one", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "name", displayName: "Full name", type: "STRING"}},
        ]}})

        const wrapper = mountForm([{id: "name", displayName: "Full name", type: "STRING"}])
        await flushPromises()

        expect(rulesFor(wrapper)).toEqual([{required: true, message: "Full name is required"}])
    })

    test("leaves optional inputs without a required rule", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "comment", type: "STRING", required: false}},
        ]}})

        const wrapper = mountForm([{id: "comment", type: "STRING", required: false}])
        await flushPromises()

        expect(rulesFor(wrapper)).toBeUndefined()
        expect(wrapper.findAllComponents(ElFormItem)[0].props("required")).toBe(false)
    })

    // SELECT/ENUM/MULTISELECT keep their own validator (it reads the flat inputsValues map rather than
    // the form model) — the new fallback must not have displaced it.
    test("keeps the existing custom validator for SELECT", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "region", type: "SELECT", values: ["us", "eu"]}},
        ]}})

        const wrapper = mountForm([{id: "region", type: "SELECT", values: ["us", "eu"]}])
        await flushPromises()

        const [rule] = rulesFor(wrapper)
        expect(rule.required).toBe(true)
        expect(typeof rule.validator).toBe("function")

        // it reports the translated message while empty, and passes once filled
        const message = await new Promise<string | undefined>(resolve =>
            rule.validator(rule, undefined, (e?: Error) => resolve(e?.message)))
        expect(message).toBe("region is required")

        vm(wrapper).inputsFormRef.inputsValues.region = "us"
        const afterFill = await new Promise<string | undefined>(resolve =>
            rule.validator(rule, undefined, (e?: Error) => resolve(e?.message)))
        expect(afterFill).toBeUndefined()
    })

    // Blurring must actually reach the el-form-item registered by the :ref callback and run its
    // validate() — the interactedInputs flag alone would only stop suppressing, never surface anything.
    // Entering the "validating" state is the observable proof that validate() was invoked.
    test("reaches the registered el-form-item and validates it on blur", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "region", type: "SELECT", values: ["us", "eu"]}},
        ]}})

        const wrapper = mountForm([{id: "region", type: "SELECT", values: ["us", "eu"]}])
        await flushPromises()
        expect(wrapper.find(".el-form-item").classes()).not.toContain("is-validating")

        await blur(wrapper)

        expect(vm(wrapper).inputsFormRef.isInteracted("region")).toBe(true)
        expect(wrapper.find(".el-form-item").classes()).toContain("is-validating")
    })

    // The registry is keyed by input id and cleared when a form item unmounts (wizard step change), so
    // onFieldBlur has to tolerate an id it no longer holds rather than throwing on the optional call.
    test("tolerates a blur for a field whose form item is no longer registered", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "region", type: "SELECT", values: ["us", "eu"]}},
        ]}})

        const wrapper = mountForm([{id: "region", type: "SELECT", values: ["us", "eu"]}])
        await flushPromises()

        expect(() => vm(wrapper).inputsFormRef.onFieldBlur("gone")).not.toThrow()
        expect(vm(wrapper).inputsFormRef.isInteracted("gone")).toBe(true)
    })
})
