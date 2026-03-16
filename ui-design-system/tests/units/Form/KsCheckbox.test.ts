import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsCheckbox from "../../../src/components/Form/KsCheckbox/KsCheckbox.vue"
import KsCheckboxGroup from "../../../src/components/Form/KsCheckbox/KsCheckboxGroup.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsCheckbox", () => {
    test("renders checkbox element", () => {
        const wrapper = mount(KsCheckbox, {
            props: {modelValue: false},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-checkbox").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsCheckbox, {
            props: {modelValue: false, disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-checkbox.is-disabled").exists()).toBe(true)
    })

    test("checked state applies is-checked class", () => {
        const wrapper = mount(KsCheckbox, {
            props: {modelValue: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-checkbox.is-checked").exists()).toBe(true)
    })
})

describe("KsCheckboxGroup", () => {
    test("renders checkbox group", () => {
        const wrapper = mount(KsCheckboxGroup, {
            props: {modelValue: []},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-checkbox-group").exists()).toBe(true)
    })

    test("small size applies correct class", () => {
        const wrapper = mount(KsCheckboxGroup, {
            props: {modelValue: [], size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-checkbox-group--small").exists()).toBe(true)
    })
})
