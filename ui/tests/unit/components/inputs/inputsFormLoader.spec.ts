import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import InputsForm from "../../../../src/components/inputs/InputsForm.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"

vi.mock("vue-router", async (importOriginal) => {
    const actual = await importOriginal<typeof import("vue-router")>()
    return {
        ...actual,
        useRoute: () => ({query: {}, params: {}, name: "flow"}),
        useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
    }
})

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, globalInjection: true}),
    ],
}

const flow = {namespace: "io.kestra.tests", id: "my_flow"} as any
const initialInputs = [{id: "region", type: "SELECT", values: ["a", "b"]}] as any

function mountForm(inputs: any = initialInputs) {
    return mount(InputsForm, {
        global: globalConfig,
        shallow: true,
        props: {flow, initialInputs: inputs},
    })
}

describe("InputsForm computing-values state", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    test("flags computing while a render call is in flight, then clears on completion", async () => {
        // Given: validation that stays pending until we resolve it
        let resolveValidate!: (value: unknown) => void
        const pending = new Promise((resolve) => {resolveValidate = resolve})
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockReturnValue(pending)

        // When: the form mounts and kicks off validation
        const wrapper = mountForm()

        // Then: it flags computing immediately for the in-flight call (no duration threshold)
        expect(wrapper.vm.isComputingValues).toBe(true)

        // And: it clears once the call resolves
        resolveValidate({data: {checks: [], inputs: []}})
        await flushPromises()
        expect(wrapper.vm.isComputingValues).toBe(false)
    })

    test("only disables dynamic inputs (expression / dependsOn) while computing", async () => {
        // Given: a static SELECT and a dynamic SELECT whose values come from an expression
        let resolveValidate!: (value: unknown) => void
        const pending = new Promise((resolve) => {resolveValidate = resolve})
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockReturnValue(pending)

        const wrapper = mountForm([
            {id: "region", type: "SELECT", values: ["a", "b"]},
            {id: "datacenter", type: "SELECT", expression: "{{ subflow(...).outputs.x }}"},
        ])

        // When: a render call is in flight
        expect(wrapper.vm.isComputingValues).toBe(true)

        // Then: only the dynamic input is flagged as computing
        expect(wrapper.vm.isComputingInput("datacenter")).toBe(true)
        expect(wrapper.vm.isComputingInput("region")).toBe(false)

        // And: nothing is flagged once the call resolves
        resolveValidate({data: {checks: [], inputs: []}})
        await flushPromises()
        expect(wrapper.vm.isComputingInput("datacenter")).toBe(false)
    })

    test("keeps a spinner but does not disable a dynamic input that already has a value during recomputations", async () => {
        // Given: an initial fetch resolving with a value, then a (pending) recomputation
        const store = useExecutionsStore()
        let resolveInitial!: (v: unknown) => void
        store.validateExecution = vi.fn()
            .mockReturnValueOnce(new Promise((r) => {resolveInitial = r}))
            .mockReturnValue(new Promise(() => {}))

        const wrapper = mountForm([
            {id: "region", type: "SELECT", values: ["us", "eu"]},
            {id: "datacenter", type: "SELECT", expression: "{{ subflow(...).outputs.x }}"},
        ])

        // During the initial fetch the empty dynamic input is fully blocked
        expect(wrapper.vm.isComputingInput("datacenter")).toBe(true)
        expect(wrapper.vm.isLoadingInput("datacenter")).toBe(true)

        // After the initial fetch completes carrying a value for the dynamic input
        resolveInitial({data: {checks: [], inputs: [
            {enabled: true, input: {id: "region", type: "SELECT", values: ["us", "eu"]}, value: "us", isDefault: true},
            {enabled: true, input: {id: "datacenter", type: "SELECT", values: ["us-1", "us-2"]}, value: "us-1", isDefault: false},
        ]}})
        await flushPromises()
        expect(wrapper.vm.isComputingValues).toBe(false)
        expect(wrapper.vm.inputsValues.datacenter).toBe("us-1")

        // When: a recomputation runs
        wrapper.vm.validateInputs()
        await flushPromises()

        // Then: the spinner shows (values may change) but the input keeps its value and stays usable
        expect(wrapper.vm.isLoadingInput("datacenter")).toBe(true)
        expect(wrapper.vm.isComputingInput("datacenter")).toBe(false)
    })

    test("disables an empty dynamic input during recomputations, not only on the initial fetch", async () => {
        // Given: an initial fetch that leaves the dynamic input empty, then a (pending) recomputation
        const store = useExecutionsStore()
        let resolveInitial!: (v: unknown) => void
        store.validateExecution = vi.fn()
            .mockReturnValueOnce(new Promise((r) => {resolveInitial = r}))
            .mockReturnValue(new Promise(() => {}))

        const wrapper = mountForm([
            {id: "region", type: "SELECT", values: ["us", "eu"]},
            {id: "datacenter", type: "SELECT", expression: "{{ subflow(...).outputs.x }}"},
        ])

        // After the initial fetch completes with no value for the dynamic input
        resolveInitial({data: {checks: [], inputs: [
            {enabled: true, input: {id: "region", type: "SELECT", values: ["us", "eu"]}, value: "us", isDefault: true},
            {enabled: true, input: {id: "datacenter", type: "SELECT", values: ["us-1", "us-2"]}, isDefault: false},
        ]}})
        await flushPromises()
        expect(wrapper.vm.isComputingValues).toBe(false)

        // When: a real value change triggers a recomputation while the dynamic input is still empty
        // (a no-op re-validate with an unchanged payload is intentionally skipped — see the dedup)
        wrapper.vm.inputsValues.region = "eu"
        wrapper.vm.onChange(wrapper.vm.inputsMetaData[0]) // region is the first input
        wrapper.vm.validateInputs()
        await flushPromises()

        // Then: the empty input is disabled with the loading placeholder (shown whenever empty)
        expect(wrapper.vm.isLoadingInput("datacenter")).toBe(true)
        expect(wrapper.vm.isComputingInput("datacenter")).toBe(true)
    })

    test("does not show the loader for a dynamic input that already has a value", async () => {
        // Given: a dynamic SELECT (expression) that has a default value
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockReturnValue(new Promise(() => {}))

        const wrapper = mountForm([
            {id: "datacenter", type: "SELECT", expression: "{{ subflow(...).outputs.x }}", defaults: "dc-1"},
        ])

        // When: the initial fetch is in flight but the input already has its default value
        expect(wrapper.vm.isComputingValues).toBe(true)
        expect(wrapper.vm.inputsValues.datacenter).toBe("dc-1")

        // Then: the loader is not shown (only shows when no value is provided)
        expect(wrapper.vm.isComputingInput("datacenter")).toBe(false)
    })

    test("ignores a stale validate response so it can't reset a value the user just picked", async () => {
        // Given: a slow initial validate (will become stale) then a fast follow-up
        const store = useExecutionsStore()
        let resolveStale!: (v: unknown) => void
        const stale = new Promise((r) => {resolveStale = r})
        store.validateExecution = vi.fn()
            .mockReturnValueOnce(stale)
            .mockResolvedValue({data: {checks: [], inputs: [
                {enabled: true, input: {id: "region", type: "SELECT", values: ["us", "eu"]}, value: "eu", isDefault: false},
            ]}})

        const wrapper = mountForm([{id: "region", type: "SELECT", values: ["us", "eu"], defaults: "us"}])
        expect(wrapper.vm.inputsValues.region).toBe("us") // default applied at init

        // When: the user picks "eu" while the initial validate is still in flight (sets value + onChange)
        wrapper.vm.inputsValues.region = "eu"
        wrapper.vm.onChange(wrapper.vm.inputsMetaData[0])
        await flushPromises()

        // And: a fresh validate (newer generation) confirms "eu"
        await wrapper.vm.validateInputs()
        expect(wrapper.vm.inputsValues.region).toBe("eu")

        // And: the slow initial response finally lands carrying the stale default
        resolveStale({data: {checks: [], inputs: [
            {enabled: true, input: {id: "region", type: "SELECT", values: ["us", "eu"]}, value: "us", isDefault: true},
        ]}})
        await flushPromises()

        // Then: the stale response was discarded — the user's pick stands
        expect(wrapper.vm.inputsValues.region).toBe("eu")
    })

    test("applies input defaults immediately, without waiting for the validate call", async () => {
        // Given: validation that never resolves during the test
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockReturnValue(new Promise(() => {}))

        // When: the form mounts with a static SELECT that has a default
        const wrapper = mountForm([
            {id: "region", type: "SELECT", values: ["us", "eu"], defaults: "us"},
        ])

        // Then: the default is applied at init, even though validate is still pending
        expect(store.validateExecution).toHaveBeenCalled()
        expect(wrapper.vm.isComputingValues).toBe(true)
        expect(wrapper.vm.inputsValues.region).toBe("us")
    })

    test("flags a dynamic input nested in a FORM as computing, by its dotted leaf id", async () => {
        // Given: a FORM group whose child is a dynamic SELECT (values from an expression).
        // The dependent leaf is matched by its dotted id (`setup.datacenter`), same as the validate
        // metadata and the template — so the loader shows for FORM-grouped (wizard) dynamic inputs too.
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockReturnValue(new Promise(() => {}))

        const wrapper = mountForm([
            {id: "setup", type: "FORM", inputs: [
                {id: "datacenter", type: "SELECT", expression: "{{ subflow(...).outputs.x }}"},
            ]},
        ])

        // When: the initial render call is in flight
        expect(wrapper.vm.isComputingValues).toBe(true)

        // Then: the FORM-nested dynamic input is flagged via its dotted leaf id
        expect(wrapper.vm.isLoadingInput("setup.datacenter")).toBe(true)
        expect(wrapper.vm.isComputingInput("setup.datacenter")).toBe(true)
    })

    test("re-validates a change made while a validate is in flight, so it is never swallowed", async () => {
        // Given: a slow initial validate (still pending), then a fast follow-up.
        // The change-watcher only attaches after the initial validate resolves, so a change made
        // during it would otherwise be lost — the post-flight re-validate is what catches it.
        const store = useExecutionsStore()
        let resolveInitial!: (v: unknown) => void
        store.validateExecution = vi.fn()
            .mockReturnValueOnce(new Promise((r) => {resolveInitial = r}))
            .mockResolvedValue({data: {checks: [], inputs: [
                {enabled: true, input: {id: "env", type: "SELECT", values: ["dev", "prod"]}, value: "prod", isDefault: false},
            ]}})

        const wrapper = mountForm([{id: "env", type: "SELECT", values: ["dev", "prod"]}])
        expect(store.validateExecution).toHaveBeenCalledTimes(1) // initial in flight

        // When: the user changes the value while the initial validate is still pending
        wrapper.vm.inputsValues.env = "prod"
        wrapper.vm.onChange(wrapper.vm.inputsMetaData[0])
        await flushPromises()

        // And: the slow initial validate finally resolves (its response is stale)
        resolveInitial({data: {checks: [], inputs: [
            {enabled: true, input: {id: "env", type: "SELECT", values: ["dev", "prod"]}, isDefault: true},
        ]}})
        await flushPromises()

        // Then: a follow-up validate fired automatically for the changed value (not swallowed)
        expect(store.validateExecution).toHaveBeenCalledTimes(2)
        expect(store.validateExecution).toHaveBeenLastCalledWith(
            expect.objectContaining({formData: expect.any(FormData)}),
        )
    })

    test("suppresses a stale per-field error on a dynamic input while it is being recomputed", async () => {
        // Given: a dynamic SELECT whose initial validate stays in flight (computing)
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockReturnValue(new Promise(() => {}))

        const wrapper = mountForm([{id: "datacenter", type: "SELECT", expression: "{{ subflow(...).outputs.x }}"}])

        // And: a stale error left in the metadata from an earlier validate (when it was still empty),
        // revealed for display (the input was edited)
        wrapper.vm.inputsMetaData[0].errors = [{message: "Missing required input:datacenter"}]
        wrapper.vm.inputsValidated.add("datacenter")
        await flushPromises()

        // Then: while the dynamic input is being (re)computed, the stale error is suppressed (no flash)
        expect(wrapper.vm.isLoadingInput("datacenter")).toBe(true)
        expect(wrapper.vm.inputError("datacenter")).toBeUndefined()
    })

    test("shows a per-field error once the dynamic input has settled (not computing)", async () => {
        // Given: a dynamic SELECT whose validate resolves carrying a value AND an error
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, input: {id: "datacenter", type: "SELECT", values: ["dc-1"]}, value: "dc-1", isDefault: false, errors: [{message: "Missing required input:datacenter"}]},
        ]}})

        const wrapper = mountForm([{id: "datacenter", type: "SELECT", expression: "{{ subflow(...).outputs.x }}", defaults: "dc-1"}])
        await flushPromises()
        wrapper.vm.inputsValidated.add("datacenter")

        // Then: once settled (not computing), the error is shown normally
        expect(wrapper.vm.isComputingValues).toBe(false)
        expect(wrapper.vm.inputError("datacenter")).toContain("Missing required")
    })

})

describe("InputsForm surfaces render errors on load, but keeps value errors gated", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    // The backend flags an error as `renderError` when the field itself couldn't render/resolve (a
    // SELECT's expression/subflow(), a dependsOn, etc.). Such errors are surfaced as soon as the input is
    // shown — no edit, no Next click, before it is ever added to inputsValidated.
    test("shows an error flagged renderError immediately, without it being validated/touched", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "region", type: "SELECT", expression: "{{ subflow(namespace='company.team', id='nope').outputs.regions }}"},
                errors: [{message: "Invalid value for input `region`. Cause: Cannot render 'expression'. Cause: Unable to find flow 'company.team'.'nope'", renderError: true}]},
        ]}})

        const wrapper = mountForm([{id: "region", type: "SELECT", expression: "{{ subflow(namespace='company.team', id='nope').outputs.regions }}"}])
        await flushPromises()

        // settled (not computing), and never touched / clicked Next
        expect(wrapper.vm.isComputingValues).toBe(false)
        expect(wrapper.vm.inputsValidated.has("region")).toBe(false)
        // ...yet the render error is surfaced immediately
        expect(wrapper.vm.inputError("region")).toContain("Unable to find flow")
    })

    // A `defaults` Pebble expression that fails to render is flagged renderError too — on a plain STRING
    // input with no `expression` at all. Proves the surfacing keys off the flag, not the SELECT expression.
    test("surfaces a defaults-render failure on a non-SELECT input (no expression)", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "name", type: "STRING", defaults: "{{ subflow(namespace='company.team', id='nope').outputs.x }}"},
                errors: [{message: "Invalid value for input `name`. Cause: Unable to find flow 'company.team'.'nope'", renderError: true}]},
        ]}})

        const wrapper = mountForm([{id: "name", type: "STRING", defaults: "{{ subflow(namespace='company.team', id='nope').outputs.x }}"}])
        await flushPromises()

        expect(wrapper.vm.isComputingValues).toBe(false)
        expect(wrapper.vm.inputsValidated.has("name")).toBe(false)
        expect(wrapper.vm.inputError("name")).toContain("Unable to find flow")
    })

    // A plain value error (e.g. "Missing required input", renderError falsy) stays gated until the user
    // interacts — preserves the "don't nag untouched fields" UX.
    test("keeps a required-but-empty error gated until the input is validated", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "region", type: "SELECT", values: ["a", "b"]},
                errors: [{message: "Invalid value for input `region`. Cause: Missing required input:region", renderError: false}]},
        ]}})

        const wrapper = mountForm([{id: "region", type: "SELECT", values: ["a", "b"]}])
        await flushPromises()

        // settled; not validated yet -> the value error is suppressed
        expect(wrapper.vm.isComputingValues).toBe(false)
        expect(wrapper.vm.inputError("region")).toBeUndefined()
        // once validated (e.g. after clicking Next) it shows
        wrapper.vm.inputsValidated.add("region")
        expect(wrapper.vm.inputError("region")).toContain("Missing required")
    })

    // A field whose `defaults` is a Pebble expression that failed to render must NOT be pre-filled with
    // the raw `{{ ... }}` template: doing so re-submits it as a provided value, the server stops
    // rendering it, and the render error is masked. Leave the value empty so the error stays visible.
    test("does not pre-fill a field with an unrendered (failed) expression default", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: false, input: {id: "name", type: "STRING", defaults: "{{ subflow(namespace='x', id='nope').outputs.y }}"},
                errors: [{message: "Invalid value for input `name`. Cause: Unable to find flow 'x'.'nope'", renderError: true}]},
        ]}})

        const wrapper = mountForm([{id: "name", type: "STRING", defaults: "{{ subflow(namespace='x', id='nope').outputs.y }}"}])
        await flushPromises()

        // the raw expression default is NOT pushed into the form value (which would mask the error)
        expect(wrapper.vm.inputsValues.name).toBeUndefined()
        // and the render error surfaces
        expect(wrapper.vm.inputError("name")).toContain("Unable to find flow")
    })

    // Regression: a successfully-rendered expression default still pre-fills (the server returns a
    // concrete value, which the form uses).
    test("still pre-fills a successfully-rendered expression default", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn().mockResolvedValue({data: {checks: [], inputs: [
            {enabled: true, isDefault: true, value: "rendered-value", input: {id: "name", type: "STRING", defaults: "{{ 'rendered-value' }}"}},
        ]}})

        const wrapper = mountForm([{id: "name", type: "STRING", defaults: "{{ 'rendered-value' }}"}])
        await flushPromises()

        expect(wrapper.vm.inputsValues.name).toBe("rendered-value")
    })
})
