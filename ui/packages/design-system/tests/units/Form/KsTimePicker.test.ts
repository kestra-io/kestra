import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTimePicker from "../../../src/components/Form/KsTimePicker.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsTimePicker", () => {
    test("renders time-picker element", () => {
        const wrapper = mount(KsTimePicker, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-date-editor").exists()).toBe(true)
    })

    test("placeholder is forwarded to input", () => {
        const wrapper = mount(KsTimePicker, {
            props: {placeholder: "Select time"},
            global: globalConfig,
        })
        expect(wrapper.find("input").attributes("placeholder")).toBe("Select time")
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsTimePicker, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".is-disabled").exists()).toBe(true)
    })

    test("small size applies correct class", () => {
        const wrapper = mount(KsTimePicker, {
            props: {size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input--small").exists()).toBe(true)
    })

    test("large size applies correct class", () => {
        const wrapper = mount(KsTimePicker, {
            props: {size: "large"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input--large").exists()).toBe(true)
    })
})
