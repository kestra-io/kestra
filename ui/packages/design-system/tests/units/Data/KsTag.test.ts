import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsTag from "../../../src/components/Data/KsTag/KsTag.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsTag", () => {
    test("renders tag element", () => {
        const wrapper = mount(KsTag, {
            slots: {default: "My Tag"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tag").exists()).toBe(true)
        expect(wrapper.text()).toBe("My Tag")
    })

    test("type prop applies correct class", () => {
        const wrapper = mount(KsTag, {
            props: {type: "success"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tag--success").exists()).toBe(true)
    })

    test("small size applies correct class", () => {
        const wrapper = mount(KsTag, {
            props: {size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tag--small").exists()).toBe(true)
    })

    test("closable renders close button", () => {
        const wrapper = mount(KsTag, {
            props: {closable: true},
            slots: {default: "Close me"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-tag.is-closable").exists()).toBe(true)
    })
})
