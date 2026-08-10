import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import InputsForm from "../../../../src/components/inputs/InputsForm.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
}

const flow = {namespace: "company.team", id: "get_data"} as any

/**
 * Stubs the validate round-trip the way `FlowInputOutput` actually behaves: `defaults` is resolved
 * server-side ONLY when the form submits no value for the input; a submitted value is echoed back
 * with `isDefault: false`. A mock that unconditionally answers "the default is true" would paper
 * over this bug, because the toggle's spurious reset writes `false` into the form and the very next
 * round-trip is what cements it as the user's own choice.
 * `defaults` itself is stringified: Input.defaults is a Property, which serialises as its expression.
 */
function stubValidate(id: string, defaults: boolean | undefined) {
    return vi.fn(({formData}: {formData?: FormData}) => {
        const submitted = formData?.get(id) ?? null
        const value = submitted !== null ? submitted === "true" : defaults
        return Promise.resolve({
            status: 200,
            headers: {},
            data: {
                checks: [],
                inputs: [{
                    enabled: true,
                    isDefault: submitted === null && defaults !== undefined,
                    value,
                    input: {id, type: "BOOL", required: false, defaults: defaults === undefined ? undefined : String(defaults)},
                }],
            },
        })
    })
}

// Mounted for real (no `shallow`): the bug lives in el-switch's own setup, which resets a
// non-boolean modelValue to false — a stubbed switch would happily accept the string "true"
// and the regression would sail straight through.
function mountForm(inputs: any[]) {
    return mount(InputsForm, {
        global: globalConfig,
        props: {flow, initialInputs: inputs as any},
    })
}

function isToggleOn(wrapper: ReturnType<typeof mountForm>, id = "mybool") {
    const input = wrapper.find(`[data-testid="input-form-${id}"] input`)
    return (input.element as HTMLInputElement).checked
}

// A BOOL `defaults` reaches the form as the STRING "true", because Input.defaults is a Property and
// Property serialises as its expression. el-switch compares modelValue against activeValue by
// identity, so "true" !== true: it emits `update:modelValue` = false during setup, which turns the
// toggle off AND runs the form's change handler. That change bumps the validate generation, so the
// in-flight response carrying the real default is dropped as stale and the follow-up round-trip
// submits `false` — the toggle is then off for good and the execution runs with the wrong value.
// Regression, fixed more than once: https://github.com/kestra-io/kestra-ee/issues/9772 (and /8978).
describe("InputsForm BOOL default", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    test("renders the toggle ON for a `defaults: true` that arrives as the string \"true\"", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate("mybool", true)

        const wrapper = mountForm([{id: "mybool", type: "BOOL", defaults: "true"}])
        await flushPromises()

        expect(wrapper.vm.inputsValues.mybool).toBe(true)
        expect(isToggleOn(wrapper)).toBe(true)
    })

    test("does not let the toggle mark itself user-edited, so the default survives validation", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate("mybool", true)

        const wrapper = mountForm([{id: "mybool", type: "BOOL", defaults: "true"}])
        await flushPromises()

        // The el-switch reset that caused the bug also fired @update:model-value, which clears
        // isDefault and makes the next validate submit `false` as if the user had picked it.
        expect(wrapper.vm.inputsMetaData[0].isDefault).toBe(true)
        expect(store.validateExecution).toHaveBeenCalledTimes(1)
        expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toEqual({mybool: true})
    })

    test("renders the toggle OFF for `defaults: false`", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate("mybool", false)

        const wrapper = mountForm([{id: "mybool", type: "BOOL", defaults: "false"}])
        await flushPromises()

        expect(wrapper.vm.inputsValues.mybool).toBe(false)
        expect(isToggleOn(wrapper)).toBe(false)
    })

    test("renders the toggle OFF when there is no default", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate("mybool", undefined)

        const wrapper = mountForm([{id: "mybool", type: "BOOL", required: false}])
        await flushPromises()

        expect(wrapper.vm.inputsValues.mybool).toBe(false)
        expect(isToggleOn(wrapper)).toBe(false)
    })

    test("still lets the user turn a defaulted-on toggle off", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate("mybool", true)

        const wrapper = mountForm([{id: "mybool", type: "BOOL", defaults: "true"}])
        await flushPromises()

        await wrapper.find("[data-testid=\"input-form-mybool\"] input").setValue(false)
        await flushPromises()

        expect(wrapper.vm.inputsValues.mybool).toBe(false)
        expect(isToggleOn(wrapper)).toBe(false)
        expect(wrapper.vm.inputsMetaData[0].isDefault).toBe(false)
    })

    // A BOOL nested in a FORM goes through the wizard's dotted-leaf path; same coercion must apply.
    test("applies the coercion to a BOOL nested in a FORM (dotted leaf id)", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate("setup.mybool", true)

        const wrapper = mountForm([
            {id: "setup", type: "FORM", inputs: [{id: "mybool", type: "BOOL", defaults: "true"}]},
        ])
        await flushPromises()

        expect(wrapper.vm.inputsValues["setup.mybool"]).toBe(true)
        expect(isToggleOn(wrapper, "setup.mybool")).toBe(true)
    })
})
