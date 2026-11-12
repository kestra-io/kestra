import {describe, test, expect} from "vitest"
import TimeRangeSwitch from "./TimeRangeSwitch.vue"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

const mountSwitch = (modelValue: "predefined" | "custom") =>
    i18nMount(TimeRangeSwitch, {props: {modelValue}})

describe("TimeRangeSwitch", () => {
    test("is checked when modelValue is custom", () => {
        const wrapper = mountSwitch("custom")

        const input = wrapper.find<HTMLInputElement>("input[type='checkbox']")
        expect(input.element.checked).toBe(true)
    })

    test("is unchecked when modelValue is predefined", () => {
        const wrapper = mountSwitch("predefined")

        const input = wrapper.find<HTMLInputElement>("input[type='checkbox']")
        expect(input.element.checked).toBe(false)
    })

    test("emits update:modelValue with custom when checked", async () => {
        const wrapper = mountSwitch("predefined")

        await wrapper.find("input[type='checkbox']").setValue(true)

        expect(wrapper.emitted("update:modelValue")).toEqual([["custom"]])
    })

    test("emits update:modelValue with predefined when unchecked", async () => {
        const wrapper = mountSwitch("custom")

        await wrapper.find("input[type='checkbox']").setValue(false)

        expect(wrapper.emitted("update:modelValue")).toEqual([["predefined"]])
    })

    test("renders both option labels", () => {
        const wrapper = mountSwitch("predefined")
        const left = wrapper.find(".switch-option.left")
        const right = wrapper.find(".switch-option.right")

        expect(left.text()).toBe(wrapper.vm.$t("filter.timerange.predefined"))
        expect(right.text()).toBe(wrapper.vm.$t("filter.timerange.custom"))
    })

    test("label for attribute matches the input id", () => {
        const wrapper = mountSwitch("predefined")

        const input = wrapper.find("input[type='checkbox']")
        const label = wrapper.find("label.switch-label")

        expect(input.attributes("id")).toBeTruthy()
        expect(label.attributes("for")).toBe(input.attributes("id"))
    })
})
