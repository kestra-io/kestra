import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsCheckTag from "../../../src/components/Data/KsTag/KsCheckTag.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsCheckTag", () => {
    test("renders check-tag element", () => {
        const wrapper = mount(KsCheckTag, {
            slots: {default: "Option A"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-check-tag").exists()).toBe(true)
        expect(wrapper.text()).toBe("Option A")
    })

    test("checked prop applies is-checked class", () => {
        const wrapper = mount(KsCheckTag, {
            props: {checked: true},
            slots: {default: "Active"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-check-tag.is-checked").exists()).toBe(true)
    })

    test("unchecked does not apply is-checked class", () => {
        const wrapper = mount(KsCheckTag, {
            props: {checked: false},
            slots: {default: "Inactive"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-check-tag.is-checked").exists()).toBe(false)
    })

    test("disabled applies is-disabled class", () => {
        const wrapper = mount(KsCheckTag, {
            props: {disabled: true},
            slots: {default: "Disabled"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-check-tag.is-disabled").exists()).toBe(true)
    })

    test("emits change event when clicked", async () => {
        const wrapper = mount(KsCheckTag, {
            props: {checked: false},
            slots: {default: "Click me"},
            global: globalConfig,
        })
        await wrapper.find(".kel-check-tag").trigger("click")
        expect(wrapper.emitted("change")).toBeTruthy()
    })
})
