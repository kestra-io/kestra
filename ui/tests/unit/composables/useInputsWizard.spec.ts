import {describe, test, expect, afterEach} from "vitest"
import {defineComponent, ref, reactive} from "vue"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {useInputsWizard} from "../../../src/composables/useInputsWizard"
import {executeFormValuesStorageKey} from "../../../src/utils/inputs"
import type {InputMetaData} from "../../../src/stores/executions"

const FLOW = {namespace: "company.team", id: "my-flow"}

// A flow with one FORM (env.region) + one ungrouped input (name) -> steps: [form, plain, recap].
const FORM_INPUTS = [
    {id: "env", type: "FORM", displayName: "Environment", inputs: [{id: "region", type: "STRING"}]},
    {id: "name", type: "STRING"},
] as unknown as InputMetaData[]

function mountWizard(meta: InputMetaData[], flow?: {tenantId?: string; namespace: string; id: string; revision?: number}) {
    let api!: ReturnType<typeof useInputsWizard>
    const inputsValues = reactive<Record<string, any>>({})
    const inputsMetaData = ref<InputMetaData[]>(meta)
    const Comp = defineComponent({
        setup() {
            api = useInputsWizard({
                props: {initialInputs: FORM_INPUTS, mode: "wizard", flow: flow as any},
                inputsMetaData,
                inputsValues,
                multiSelectInputs: reactive({}),
                inputsValidated: ref(new Set<string>()),
                validateInputs: async () => {},
                onRecapChange: () => {},
            })
            return () => null
        },
    })
    mount(Comp, {global: {plugins: [createI18n({legacy: false, locale: "en"})]}})
    return {api, inputsValues, inputsMetaData}
}

describe("useInputsWizard visited / stepStatus", () => {
    test("active step is process, others wait; no step filled initially", () => {
        const {api} = mountWizard([
            {id: "env.region", type: "STRING", required: true} as InputMetaData,
            {id: "name", type: "STRING", required: true} as InputMetaData,
        ])
        expect(api.isWizard.value).toBe(true)
        expect(api.stepStatus(0)).toBe("process") // currentStep = 0
        expect(api.stepStatus(1)).toBe("wait")
        expect(api.visited.value.size).toBe(0)
    })

    test("goNext marks the passed step visited (filled) and advances active", async () => {
        const {api, inputsValues} = mountWizard([
            {id: "env.region", type: "STRING", required: true} as InputMetaData,
            {id: "name", type: "STRING", required: true} as InputMetaData,
        ])
        inputsValues["env.region"] = "us-east" // make step 0 valid so goNext advances
        await api.goNext()
        await flushPromises()
        expect(api.currentStep.value).toBe(1)
        expect(api.visited.value.has(0)).toBe(true)
        expect(api.stepStatus(0)).toBe("success") // visited + not active
        expect(api.stepStatus(1)).toBe("process") // now active
    })

    test("goNext does NOT advance or mark visited when the step is invalid", async () => {
        const {api} = mountWizard([
            {id: "env.region", type: "STRING", required: true} as InputMetaData,
            {id: "name", type: "STRING", required: true} as InputMetaData,
        ])
        // env.region has no value -> stepIsValid false
        await api.goNext()
        await flushPromises()
        expect(api.currentStep.value).toBe(0)
        expect(api.visited.value.size).toBe(0)
    })
})

describe("useInputsWizard persistValues / restorePersistedValues", () => {
    const storageKey = executeFormValuesStorageKey(FLOW)!

    afterEach(() => localStorage.removeItem(storageKey))

    test("persistValues never writes SECRET values to localStorage", () => {
        const {api, inputsValues} = mountWizard(
            [
                {id: "env.region", type: "STRING", required: true} as InputMetaData,
                {id: "name", type: "SECRET", required: true} as InputMetaData,
            ],
            FLOW,
        )
        inputsValues["env.region"] = "us-east"
        inputsValues["name"] = "s3cr3t-token"

        api.persistValues()

        const stored = JSON.parse(localStorage.getItem(storageKey)!)
        expect(stored).toEqual({"env.region": "us-east"})
        expect(JSON.stringify(stored)).not.toContain("s3cr3t-token")
    })

    test("restorePersistedValues leaves SECRET fields unset after a reload", () => {
        localStorage.setItem(storageKey, JSON.stringify({"env.region": "us-east"}))
        const {api, inputsValues} = mountWizard(
            [
                {id: "env.region", type: "STRING", required: true} as InputMetaData,
                {id: "name", type: "SECRET", required: true} as InputMetaData,
            ],
            FLOW,
        )

        api.restorePersistedValues()

        expect(inputsValues["env.region"]).toBe("us-east")
        expect(inputsValues["name"]).toBeUndefined()
    })
})
