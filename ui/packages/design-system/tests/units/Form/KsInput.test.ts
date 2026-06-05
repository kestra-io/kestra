import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsInput from "../../../src/components/Form/KsInput.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsInput", () => {
    test("renders input element", () => {
        const wrapper = mount(KsInput, {
            props: {placeholder: "Enter text"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input").exists()).toBe(true)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsInput, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input.is-disabled").exists()).toBe(true)
    })

    test("small size applies correct class", () => {
        const wrapper = mount(KsInput, {
            props: {size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input--small").exists()).toBe(true)
    })

    test("large size applies correct class", () => {
        const wrapper = mount(KsInput, {
            props: {size: "large"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input--large").exists()).toBe(true)
    })

    test("textarea type renders textarea element", () => {
        const wrapper = mount(KsInput, {
            props: {type: "textarea"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-textarea").exists()).toBe(true)
    })

    test("clearable reserves clear-icon space to prevent reflow", () => {
        const wrapper = mount(KsInput, {
            props: {clearable: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-input.ks-input--reserve-clear").exists()).toBe(true)
    })

    test("non-clearable does not reserve clear-icon space", () => {
        const wrapper = mount(KsInput, {
            global: globalConfig,
        })
        expect(wrapper.find(".ks-input--reserve-clear").exists()).toBe(false)
    })

    test("clearable with a suffix slot does not reserve clear-icon space", () => {
        const wrapper = mount(KsInput, {
            props: {clearable: true},
            slots: {suffix: "<span>x</span>"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-input--reserve-clear").exists()).toBe(false)
    })

    test("clearable password input does not reserve clear-icon space", () => {
        const wrapper = mount(KsInput, {
            props: {clearable: true, showPassword: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-input--reserve-clear").exists()).toBe(false)
    })
})
