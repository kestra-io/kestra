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

        // When: a recomputation runs while the input is still empty
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
})
