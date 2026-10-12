import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import InputsForm from "./InputsForm.vue"
import {useExecutionsStore, type InputMetaData, type ValidationResponse} from "../../stores/executions"
import type {Flow} from "../../stores/flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const globalConfig = {
    plugins: [KestraDesignSystem],
}

const flow = {namespace: "company.team", id: "get_data"} as Flow

function stubValidate(input: Record<string, unknown>) {
    return vi.fn((): Promise<ValidationResponse> => Promise.resolve({
        checks: [],
        inputs: [{
            enabled: true,
            isDefault: false,
            input: input as InputMetaData,
        }],
    }))
}

function mountForm(input: Record<string, unknown>) {
    const store = useExecutionsStore()
    store.validateExecution = stubValidate(input)
    return i18nMount(InputsForm, {
        global: globalConfig,
        shallow: false,
        props: {flow, initialInputs: [input as InputMetaData]},
    })
}

describe("InputsForm number limits", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    it("should respect min: 0 and max: 0 on INT inputs", async () => {
        const wrapper = mountForm({id: "myint", type: "INT", min: 0, max: 0})
        await flushPromises()

        const inputNumber = wrapper.findComponent("[data-testid='input-form-myint']")
        expect(inputNumber.exists()).toBe(true)
        expect(inputNumber.props("min")).toBe(0)
        expect(inputNumber.props("max")).toBe(0)

        const hint = wrapper.find(".hint")
        expect(hint.exists()).toBe(true)
        expect(hint.text()).toBe("Minimum value is 0, maximum value is 0.")
    })

    it("should respect max: 0 without min on INT inputs", async () => {
        const wrapper = mountForm({id: "myint", type: "INT", max: 0})
        await flushPromises()

        const inputNumber = wrapper.findComponent("[data-testid='input-form-myint']")
        expect(inputNumber.exists()).toBe(true)
        expect(inputNumber.props("min")).toBeUndefined()
        expect(inputNumber.props("max")).toBe(0)

        const hint = wrapper.find(".hint")
        expect(hint.exists()).toBe(true)
        expect(hint.text()).toBe("Maximum value is 0.")
    })

    it("should respect min: 0 and max: 0 on FLOAT inputs", async () => {
        const wrapper = mountForm({id: "myfloat", type: "FLOAT", min: 0, max: 0})
        await flushPromises()

        const inputNumber = wrapper.findComponent("[data-testid='input-form-myfloat']")
        expect(inputNumber.exists()).toBe(true)
        expect(inputNumber.props("min")).toBe(0)
        expect(inputNumber.props("max")).toBe(0)

        const hint = wrapper.find(".hint")
        expect(hint.exists()).toBe(true)
        expect(hint.text()).toBe("Minimum value is 0, maximum value is 0.")
    })

    it("should respect max: 0 without min on FLOAT inputs", async () => {
        const wrapper = mountForm({id: "myfloat", type: "FLOAT", max: 0})
        await flushPromises()

        const inputNumber = wrapper.findComponent("[data-testid='input-form-myfloat']")
        expect(inputNumber.exists()).toBe(true)
        expect(inputNumber.props("min")).toBeUndefined()
        expect(inputNumber.props("max")).toBe(0)

        const hint = wrapper.find(".hint")
        expect(hint.exists()).toBe(true)
        expect(hint.text()).toBe("Maximum value is 0.")
    })

    it("should fall back to Infinity for max when min > max", async () => {
        const wrapper = mountForm({id: "myint", type: "INT", min: 5, max: 0})
        await flushPromises()

        const inputNumber = wrapper.findComponent("[data-testid='input-form-myint']")
        expect(inputNumber.exists()).toBe(true)
        expect(inputNumber.props("min")).toBe(5)
        expect(inputNumber.props("max")).toBe(Infinity)

        const hint = wrapper.find(".hint")
        expect(hint.exists()).toBe(true)
        expect(hint.text()).toContain("removed the upper limit")
    })
})
