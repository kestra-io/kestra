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
 * server-side ONLY when the form submits no value for the input, and a submitted value is echoed
 * back with `isDefault: false`. Clearing an input submits nothing at all — `normalizeInputValues`
 * drops empty strings — so a mock that ignored the payload would hide the bug covered here.
 */
function stubValidate(id: string, defaults: string) {
    return vi.fn(({formData}: {formData?: FormData}) => {
        const submitted = formData?.get(id) ?? null
        return Promise.resolve({
            status: 200,
            headers: {},
            data: {
                checks: [],
                inputs: [{
                    enabled: true,
                    isDefault: submitted === null,
                    value: submitted ?? defaults,
                    input: {id, type: "STRING", required: false, defaults},
                }],
            },
        })
    })
}

// Answers with a server-resolved value whatever the form submits, as it does for an input the server
// renders itself (`expression` / `dependsOn`) and for the inputs of a freshly selected flow.
function stubAlwaysResolving(id: string, value: string, input: Record<string, unknown> = {}) {
    return vi.fn(() => Promise.resolve({
        status: 200,
        headers: {},
        data: {
            checks: [],
            inputs: [{
                enabled: true,
                isDefault: true,
                value,
                input: {id, type: "STRING", required: false, ...input},
            }],
        },
    }))
}

// `shallow` keeps KsEditor out of jsdom: a STRING input renders as Monaco, which cannot be typed
// into here.
function mountForm(inputs: any[]) {
    return mount(InputsForm, {
        global: globalConfig,
        shallow: true,
        props: {flow, initialInputs: inputs as any},
    })
}

/**
 * One user edit plus the validate round-trip it triggers. The value is written and the change handler
 * fired the way KsEditor's `v-model` and `@update:model-value` do it; the edit then has to settle
 * before the clock moves, because the watcher that schedules the debounced validation only runs on
 * flush — advancing timers first leaves the 500ms debounce unscheduled and nothing is sent.
 */
async function editAndSettle(wrapper: ReturnType<typeof mountForm>, value: string, id = "mystring") {
    wrapper.vm.inputsValues[id] = value
    wrapper.vm.onChange(wrapper.vm.inputsMetaData.find((it: {id: string}) => it.id === id)!)
    await flushPromises()
    vi.advanceTimersByTime(600)
    await flushPromises()
}

// Clearing an input submits nothing, so the validate round-trip answers `isDefault: true` for it and
// `updateDefaults()` wrote the default straight back into the field the user had just emptied —
// landing on top of whatever they typed next, cursor included.
// https://github.com/kestra-io/kestra/issues/17897
describe("InputsForm STRING default", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.useFakeTimers()
    })

    afterEach(() => {
        vi.useRealTimers()
        document.body.innerHTML = ""
    })

    test("leaves a cleared input empty instead of refilling it from `defaults`", async () => {
        const store = useExecutionsStore()
        const validate = stubValidate("mystring", "hello")
        store.validateExecution = validate

        const wrapper = mountForm([{id: "mystring", type: "STRING", defaults: "hello"}])
        // The watcher that debounces validation is installed only once this first call resolves.
        await flushPromises()

        expect(wrapper.vm.inputsValues.mystring).toBe("hello")

        // Typing before clearing is what puts a value on the wire, so that emptying the field is a
        // payload change the validate round-trip acts on rather than a no-op it dedupes away.
        await editAndSettle(wrapper, "world")
        expect(wrapper.vm.inputsValues.mystring).toBe("world")

        const roundTripsBeforeClear = validate.mock.calls.length
        await editAndSettle(wrapper, "")

        // Asserted, not assumed: without a round-trip landing while the field is empty there is no
        // default to write back, and this test would pass while protecting nothing.
        expect(validate.mock.calls.length).toBeGreaterThan(roundTripsBeforeClear)
        expect(wrapper.vm.inputsValues.mystring).toBe("")
    })

    // A leaf of a FORM reaches the form under its dotted id, which is the key the guard is keyed on.
    test("leaves a cleared FORM-nested input empty too", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate("setup.mystring", "hello")

        const wrapper = mountForm([
            {id: "setup", type: "FORM", inputs: [{id: "mystring", type: "STRING", defaults: "hello"}]},
        ])
        await flushPromises()

        expect(wrapper.vm.inputsValues["setup.mystring"]).toBe("hello")

        await editAndSettle(wrapper, "world", "setup.mystring")
        await editAndSettle(wrapper, "", "setup.mystring")

        expect(wrapper.vm.inputsValues["setup.mystring"]).toBe("")
    })

    test("still applies the default to an input the user never touched", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate("mystring", "hello")

        const wrapper = mountForm([{id: "mystring", type: "STRING", defaults: "hello"}])
        await flushPromises()

        vi.advanceTimersByTime(600)
        await flushPromises()

        expect(wrapper.vm.inputsValues.mystring).toBe("hello")
        expect(wrapper.vm.inputsMetaData[0].isDefault).toBe(true)
    })

    // A `dependsOn` input is meant to be recomputed server-side, so the guard that protects a
    // user-edited input from its default must not freeze one of those.
    test("still lets the server recompute a dependsOn input the user has edited", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubAlwaysResolving("mystring", "recomputed", {dependsOn: ["other"]})

        const wrapper = mountForm([{id: "mystring", type: "STRING", dependsOn: ["other"]}])
        await flushPromises()

        await editAndSettle(wrapper, "mine")

        expect(wrapper.vm.inputsValues.mystring).toBe("recomputed")
    })

    // The form is not remounted when another flow is selected, so an edit made to the previous one
    // must not keep the new flow's inputs from taking their defaults.
    test("lets a newly selected flow's inputs take their defaults again", async () => {
        const store = useExecutionsStore()
        store.validateExecution = stubValidate("mystring", "hello")

        const wrapper = mountForm([{id: "mystring", type: "STRING", defaults: "hello"}])
        await flushPromises()

        await editAndSettle(wrapper, "world")
        expect(wrapper.vm.inputsValues.mystring).toBe("world")

        store.validateExecution = stubAlwaysResolving("mystring", "hello", {defaults: "hello"})
        await wrapper.setProps({flow: {namespace: "company.team", id: "other_flow"} as any})
        await flushPromises()

        expect(wrapper.vm.inputsValues.mystring).toBe("hello")
    })
})
