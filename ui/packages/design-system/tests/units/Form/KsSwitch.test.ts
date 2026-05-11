import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsSwitch from "../../../src/components/Form/KsSwitch.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsSwitch", () => {
    test("renders switch element", () => {
        const wrapper = mount(KsSwitch, {
            props: {modelValue: false},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-switch").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsSwitch, {
            props: {modelValue: false, disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-switch.is-disabled").exists()).toBe(true)
    })

    test("active state applies is-checked class", () => {
        const wrapper = mount(KsSwitch, {
            props: {modelValue: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-switch.is-checked").exists()).toBe(true)
    })
})
