import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"

import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem, {KsInputNumber} from "@kestra-io/design-system"
import InputsForm from "./InputsForm.vue"
import {useExecutionsStore, type InputMetaData} from "../../stores/executions"
import type {Flow} from "../../stores/flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const flow = {namespace: "company.team", id: "limits"} as Flow

async function mountNumber(type: "INT" | "FLOAT", limits: Record<string, unknown>) {
    const input = {id: "num", type, required: false, ...limits}
    useExecutionsStore().validateExecution = vi.fn(() => Promise.resolve({
        checks: [],
        inputs: [{enabled: true, isDefault: false, value: undefined, input}],
    }))

    const wrapper = i18nMount(InputsForm, {
        global: {plugins: [KestraDesignSystem]},
        props: {flow, initialInputs: [input] as InputMetaData[]},
    })
    await flushPromises()

    return {
        field: wrapper.findComponent(KsInputNumber),
        hint: wrapper.find(".hint"),
    }
}

describe("InputsForm number limits", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    test.each(["INT", "FLOAT"] as const)("enforces and describes max: 0 on %s", async (type) => {
        const {field, hint} = await mountNumber(type, {min: -10, max: 0})

        expect(field.props("max")).toBe(0)
        expect(field.props("min")).toBe(-10)
        expect(hint.text()).toBe("Minimum value is -10, maximum value is 0.")
    })

    test.each(["INT", "FLOAT"] as const)("shows the hint for min: 0 alone on %s", async (type) => {
        const {field, hint} = await mountNumber(type, {min: 0})

        expect(field.props("min")).toBe(0)
        expect(field.props("max")).toBe(Infinity)
        expect(hint.text()).toBe("Minimum value is 0.")
    })

    test.each([
        ["missing", {}],
        ["null", {min: null, max: null}],
        ["empty", {min: "", max: ""}],
    ])("treats a limit given as %s as no limit", async (_, limits) => {
        const {field, hint} = await mountNumber("INT", limits)

        expect(field.props("min")).toBe(-Infinity)
        expect(field.props("max")).toBe(Infinity)
        expect(hint.exists()).toBe(false)
    })

    test("removes the upper limit when min is larger than max", async () => {
        const {field, hint} = await mountNumber("INT", {min: 5, max: 0})

        expect(field.props("max")).toBe(Infinity)
        expect(hint.text()).toBe("Minimum value 5 is larger than maximum value 0, so we've removed the upper limit.")
    })
})
