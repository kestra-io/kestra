import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsAlert from "../../../src/components/Feedback/KsAlert.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsAlert", () => {
    test("renders alert element", () => {
        const wrapper = mount(KsAlert, {
            props: {type: "info", title: "Info"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert").exists()).toBe(true)
    })

    test("type prop applies correct class", () => {
        const wrapper = mount(KsAlert, {
            props: {type: "warning"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert--warning").exists()).toBe(true)
    })

    test("error type applies correct class", () => {
        const wrapper = mount(KsAlert, {
            props: {type: "error"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert--error").exists()).toBe(true)
    })

    test("title prop renders title text", () => {
        const wrapper = mount(KsAlert, {
            props: {type: "info", title: "Test Alert Title"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("Test Alert Title")
    })
})
