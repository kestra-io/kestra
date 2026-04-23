import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsText from "../../../src/components/Basic/KsText.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsText", () => {
    test("renders text element", () => {
        const wrapper = mount(KsText, {
            slots: {default: "Hello World"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-text").exists()).toBe(true)
        expect(wrapper.text()).toBe("Hello World")
    })

    test("type prop applies correct class", () => {
        const wrapper = mount(KsText, {
            props: {type: "success"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-text--success").exists()).toBe(true)
    })

    test("size prop applies correct class", () => {
        const wrapper = mount(KsText, {
            props: {size: "large"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-text--large").exists()).toBe(true)
    })
})
