import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsScrollbar from "../../../src/components/Basic/KsScrollbar.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsScrollbar", () => {
    test("renders scrollbar element", () => {
        const wrapper = mount(KsScrollbar, {
            slots: {default: "<div>Content</div>"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-scrollbar").exists()).toBe(true)
    })

    test("accepts maxHeight prop", () => {
        const wrapper = mount(KsScrollbar, {
            props: {maxHeight: "200px"},
            slots: {default: "<div>Content</div>"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-scrollbar").exists()).toBe(true)
    })

    test("exposes scrollTo method", () => {
        const wrapper = mount(KsScrollbar, {
            slots: {default: "<div>Content</div>"},
            global: globalConfig,
        })
        expect(typeof wrapper.vm.scrollTo).toBe("function")
    })
})
