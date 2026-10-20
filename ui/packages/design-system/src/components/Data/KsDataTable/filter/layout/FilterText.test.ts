import {describe, test, expect} from "vitest"
import FilterText from "./FilterText.vue"
import KsInput from "../../../../Form/KsInput.vue"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

const mountFilterText = (label: string, textValue: string) =>
    i18nMount(FilterText, {props: {label, textValue}})

describe("FilterText", () => {
    test("binds the current value to the input", () => {
        const wrapper = mountFilterText("Name", "abc")

        expect(wrapper.findComponent(KsInput).props("modelValue")).toBe("abc")
    })

    test("builds the placeholder from the lowercased label", () => {
        const wrapper = mountFilterText("Namespace", "")

        expect(wrapper.findComponent(KsInput).props("placeholder")).toBe("Enter namespace")
    })

    test("emits the typed value", async () => {
        const wrapper = mountFilterText("Name", "")

        await wrapper.find("input").setValue("abc")

        expect(wrapper.emitted("update:textValue")).toEqual([["abc"]])
    })

    test("emits the value as a string", async () => {
        const wrapper = mountFilterText("Name", "")

        wrapper.findComponent(KsInput).vm.$emit("update:modelValue", 42)
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted("update:textValue")).toEqual([["42"]])
    })
})
