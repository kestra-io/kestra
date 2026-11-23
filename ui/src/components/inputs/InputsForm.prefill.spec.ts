import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import InputsForm from "./InputsForm.vue"
import {useExecutionsStore, type InputMetaData} from "../../stores/executions"
import type {Flow} from "../../stores/flow"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

describe("InputsForm validated values and prefill", () => {
    beforeEach(() => setActivePinia(createPinia()))
    afterEach(() => {document.body.innerHTML = ""})

    test.each<[InputMetaData["type"], unknown, unknown, unknown]>([
        ["INT", 0, 7, 0],
        ["BOOL", false, true, false],
        ["STRING", "", "fallback", "fallback"],
        ["STRING", null, "fallback", "fallback"],
        ["STRING", undefined, "fallback", "fallback"],
        ["STRING", "chosen", "fallback", "chosen"],
    ])("preserves the resolved %s value %s with prefill %s", async (type, value, prefill, expected) => {
        const input: InputMetaData = {id: "answer", type, prefill, required: false}
        const store = useExecutionsStore()
        store.validateExecution = vi.fn(async () => ({
            inputs: [{enabled: true, isDefault: true, value, input}],
        }))
        const wrapper = i18nMount(InputsForm, {
            shallow: true,
            global: {plugins: [KestraDesignSystem]},
            props: {flow: {namespace: "company.team", id: "prefill"} as Flow, initialInputs: [input]},
        })
        await flushPromises()

        expect(wrapper.vm.inputsValues.answer).toBe(expected)
        expect(wrapper.emitted("update:modelValue")?.at(-1)?.[0]).toEqual({answer: expected})
    })
})
