import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsConfigProvider from "../../../src/components/Configuration/KsConfigProvider.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsConfigProvider", () => {
    test("renders slot content", () => {
        const wrapper = mount(KsConfigProvider, {
            slots: {default: "<div class='app-content'>App</div>"},
            global: globalConfig,
        })
        expect(wrapper.find(".app-content").exists()).toBe(true)
        expect(wrapper.text()).toBe("App")
    })

    test("size prop is accepted without error", () => {
        const wrapper = mount(KsConfigProvider, {
            props: {size: "small"},
            slots: {default: "<span>Content</span>"},
            global: globalConfig,
        })
        expect(wrapper.find("span").exists()).toBe(true)
    })

    test("zIndex prop is accepted without error", () => {
        const wrapper = mount(KsConfigProvider, {
            props: {zIndex: 2000},
            slots: {default: "<span>Content</span>"},
            global: globalConfig,
        })
        expect(wrapper.find("span").exists()).toBe(true)
    })
})
