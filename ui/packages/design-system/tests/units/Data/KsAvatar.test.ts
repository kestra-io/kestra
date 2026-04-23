import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsAvatar from "../../../src/components/Data/KsAvatar.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsAvatar", () => {
    test("renders avatar element", () => {
        const wrapper = mount(KsAvatar, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-avatar").exists()).toBe(true)
    })

    test("square shape applies correct class", () => {
        const wrapper = mount(KsAvatar, {
            props: {shape: "square"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-avatar--square").exists()).toBe(true)
    })

    test("large size applies correct class", () => {
        const wrapper = mount(KsAvatar, {
            props: {size: "large"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-avatar--large").exists()).toBe(true)
    })

    test("default slot renders fallback content", () => {
        const wrapper = mount(KsAvatar, {
            slots: {default: "JD"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("JD")
    })
})
