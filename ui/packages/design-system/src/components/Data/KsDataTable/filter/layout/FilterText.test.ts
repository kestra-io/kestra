import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../../../index"
import FilterText from "./FilterText.vue"
import KsInput from "../../../../Form/KsInput.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const mountFilterText = (label: string, textValue: string) => mount(FilterText, {
    props: {label, textValue},
    global: globalConfig,
})

describe("FilterText", () => {
    test("binds the current textValue to the input", () => {
        const wrapper = mountFilterText("Name", "orders")

        expect((wrapper.find("input").element as HTMLInputElement).value).toBe("orders")
    })

    test("sets the placeholder to Enter and the lowercased label", () => {
        const wrapper = mountFilterText("Namespace", "")

        expect(wrapper.find("input").attributes("placeholder")).toBe("Enter namespace")
    })

    test("emits update:textValue with the typed value", async () => {
        const wrapper = mountFilterText("Name", "")

        await wrapper.find("input").setValue("orders")

        expect(wrapper.emitted("update:textValue")).toEqual([["orders"]])
    })

    test("emits a string when the input hands over a non-string", async () => {
        const wrapper = mountFilterText("Name", "")

        wrapper.findComponent(KsInput).vm.$emit("update:modelValue", 42)
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted("update:textValue")).toEqual([["42"]])
    })

    test("emits an empty string when the input is cleared", async () => {
        const wrapper = mountFilterText("Name", "orders")

        await wrapper.find("input").setValue("")

        expect(wrapper.emitted("update:textValue")).toEqual([[""]])
    })
})
