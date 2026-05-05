import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsInputNumber from "../../../src/components/Form/KsInputNumber.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsInputNumber", () => {
    test("renders input-number element", () => {
        const wrapper = mount(KsInputNumber, {
            props: {modelValue: 1},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input-number").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsInputNumber, {
            props: {modelValue: 0, disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input-number.is-disabled").exists()).toBe(true)
    })

    test("small size applies correct class", () => {
        const wrapper = mount(KsInputNumber, {
            props: {modelValue: 0, size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input-number--small").exists()).toBe(true)
    })

    test("controls-right applies correct class", () => {
        const wrapper = mount(KsInputNumber, {
            props: {modelValue: 0, controlsPosition: "right"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input-number.is-controls-right").exists()).toBe(true)
    })
})
