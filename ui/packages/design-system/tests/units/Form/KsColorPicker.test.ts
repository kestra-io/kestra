import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsColorPicker from "../../../src/components/Form/KsColorPicker.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsColorPicker", () => {
    test("renders color-picker element", () => {
        const wrapper = mount(KsColorPicker, {
            props: {modelValue: "#409EFF"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-color-picker").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsColorPicker, {
            props: {modelValue: "#409EFF", disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-color-picker.is-disabled").exists()).toBe(true)
    })

    test("small size applies correct class", () => {
        const wrapper = mount(KsColorPicker, {
            props: {modelValue: "#409EFF", size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-color-picker--small").exists()).toBe(true)
    })
})
