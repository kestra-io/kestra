import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {nextTick} from "vue"
import {i18nMount} from "../../i18nMount"

import {createPinia, setActivePinia} from "pinia"
import en from "../../../../src/translations/en.json"
import InputsForm from "../../../../src/components/inputs/InputsForm.vue"
import {useExecutionsStore, type ValidationResponse, type InputMetaData} from "../../../../src/stores/executions"
import type {Flow} from "../../../../src/stores/flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const flow = {namespace: "company.team", id: "get_data"} as Flow
const DEFAULT = "nsfile:///<b>x</b>.txt"

function stubValidate(): Promise<ValidationResponse> {
    return Promise.resolve({
        checks: [],
        inputs: [{
            enabled: true,
            isDefault: true,
            value: DEFAULT,
            input: {id: "myfile", type: "FILE", required: false, defaults: DEFAULT},
        }],
    })
}

/** The placeholder renders through v-html because its translation carries <code>, so the name itself has to be escaped. */
describe("InputsForm FILE placeholder", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    test("renders the file name as text rather than as markup", async () => {
        const store = useExecutionsStore()
        store.validateExecution = vi.fn(stubValidate)

        const wrapper = i18nMount(InputsForm, {
            locales: en,
            props: {flow, initialInputs: [{id: "myfile", type: "FILE", defaults: DEFAULT}] as InputMetaData[]},
        })
        await flushPromises()

        const placeholder = wrapper.find(".file-placeholder")
        expect(placeholder.element.querySelector("code")).not.toBeNull()
        expect(placeholder.element.querySelector("b")).toBeNull()
        expect(placeholder.text()).toContain("<b>x</b>.txt")

        wrapper.vm.inputsValues.myfile = new File(["x"], "<b>y</b>.txt")
        await nextTick()

        expect(placeholder.element.querySelector("b")).toBeNull()
        expect(placeholder.text()).toBe("<b>y</b>.txt")

        wrapper.vm.inputsValues.myfile = "nsfile:///a&b \"c\".txt"
        await nextTick()

        expect(placeholder.text()).toContain("a&b \"c\".txt")
    })
})
