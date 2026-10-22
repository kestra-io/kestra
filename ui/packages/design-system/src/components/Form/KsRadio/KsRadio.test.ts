import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../index"
import KsRadio from "./KsRadio.vue"
import KsRadioGroup from "./KsRadioGroup.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsRadio", () => {
    test("renders radio element", () => {
        const wrapper = mount(KsRadio, {
            props: {modelValue: "a", value: "a"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-radio").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsRadio, {
            props: {modelValue: "a", value: "a", disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-radio.is-disabled").exists()).toBe(true)
    })

    test("checked state applies is-checked class", () => {
        const wrapper = mount(KsRadio, {
            props: {modelValue: "a", value: "a"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-radio.is-checked").exists()).toBe(true)
    })
})

describe("KsRadioGroup", () => {
    test("renders radio group", () => {
        const wrapper = mount(KsRadioGroup, {
            props: {modelValue: "a"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-radio-group").exists()).toBe(true)
    })

    test("small size applies correct class", () => {
        const wrapper = mount(KsRadioGroup, {
            props: {modelValue: "a", size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-radio-group--small").exists()).toBe(true)
    })
})
