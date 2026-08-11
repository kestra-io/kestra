import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsPassword from "../../../src/components/Form/KsPassword.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsPassword", () => {
    test("renders the password container", () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: ""},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-password").exists()).toBe(true)
    })

    test("renders textarea input", () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: ""},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-textarea").exists()).toBe(true)
    })

    test("applies masked class when hidden (default)", () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: ""},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-password--masked").exists()).toBe(true)
    })

    test("applies masked class when disabled", () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: "secret", disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-password--masked").exists()).toBe(true)
    })

    test("hides toggle button when disabled", () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: "secret", disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button").exists()).toBe(false)
    })

    test("hides toggle button when modelValue is empty", () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: ""},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button").exists()).toBe(false)
    })

    test("shows toggle button when not disabled and value is set", () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: "secret"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-button").exists()).toBe(true)
    })

    test("clicking toggle button removes masked class", async () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: "secret"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-password--masked").exists()).toBe(true)
        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.find(".ks-password--masked").exists()).toBe(false)
    })

    test("clicking toggle button twice restores masked class", async () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: "secret"},
            global: globalConfig,
        })
        await wrapper.find(".kel-button").trigger("click")
        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.find(".ks-password--masked").exists()).toBe(true)
    })

    test("resets to hidden when disabled prop becomes true", async () => {
        const wrapper = mount(KsPassword, {
            props: {modelValue: "secret", disabled: false},
            global: globalConfig,
        })
        // Reveal the value
        await wrapper.find(".kel-button").trigger("click")
        expect(wrapper.find(".ks-password--masked").exists()).toBe(false)

        // Disable the component
        await wrapper.setProps({disabled: true})
        await wrapper.vm.$nextTick()

        // Masked class should be back
        expect(wrapper.find(".ks-password--masked").exists()).toBe(true)
    })
})
