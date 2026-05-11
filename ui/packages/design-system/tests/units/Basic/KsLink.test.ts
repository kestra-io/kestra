import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsLink from "../../../src/components/Basic/KsLink.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsLink", () => {
    test("renders link element", () => {
        const wrapper = mount(KsLink, {
            slots: {default: "Click me"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-link").exists()).toBe(true)
        expect(wrapper.text()).toContain("Click me")
    })

    test("type prop applies correct class", () => {
        const wrapper = mount(KsLink, {
            props: {type: "primary"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-link--primary").exists()).toBe(true)
    })

    test("disabled prop applies disabled class", () => {
        const wrapper = mount(KsLink, {
            props: {disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-link.is-disabled").exists()).toBe(true)
    })
})
