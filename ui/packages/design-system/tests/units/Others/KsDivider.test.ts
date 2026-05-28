import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsDivider from "../../../src/components/Others/KsDivider.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsDivider", () => {
    test("renders divider element", () => {
        const wrapper = mount(KsDivider, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-divider").exists()).toBe(true)
    })

    test("vertical direction applies correct class", () => {
        const wrapper = mount(KsDivider, {
            props: {direction: "vertical"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-divider--vertical").exists()).toBe(true)
    })

    test("default slot renders text", () => {
        const wrapper = mount(KsDivider, {
            slots: {default: "Section Title"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("Section Title")
    })
})
