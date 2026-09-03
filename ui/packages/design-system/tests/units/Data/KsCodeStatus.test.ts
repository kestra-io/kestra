import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsCodeStatus from "../../../src/components/Data/KsCodeStatus.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsCodeStatus", () => {
    test("renders root with valid modifier class", () => {
        const wrapper = mount(KsCodeStatus, {
            props: {status: "valid"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-code-status").exists()).toBe(true)
        expect(wrapper.find(".ks-code-status--valid").exists()).toBe(true)
    })

    test("renders root with error modifier class", () => {
        const wrapper = mount(KsCodeStatus, {
            props: {status: "error"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-code-status--error").exists()).toBe(true)
    })

    test("renders the valid icon", () => {
        const wrapper = mount(KsCodeStatus, {
            props: {status: "valid"},
            global: globalConfig,
        })
        expect(wrapper.find(".check-circle-outline-icon").exists()).toBe(true)
    })

    test("renders the error icon", () => {
        const wrapper = mount(KsCodeStatus, {
            props: {status: "error"},
            global: globalConfig,
        })
        expect(wrapper.find(".alert-box-outline-icon").exists()).toBe(true)
    })

    test("renders the label prop", () => {
        const wrapper = mount(KsCodeStatus, {
            props: {status: "valid", label: "Valid"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-code-status__text").text()).toBe("Valid")
    })

    test("renders default slot content", () => {
        const wrapper = mount(KsCodeStatus, {
            props: {status: "error"},
            slots: {default: "Custom content"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-code-status__text").text()).toBe("Custom content")
    })

    test("default slot takes precedence over label prop", () => {
        const wrapper = mount(KsCodeStatus, {
            props: {status: "error", label: "From prop"},
            slots: {default: "From slot"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-code-status__text").text()).toBe("From slot")
    })

    test("renders text wrapper even when no label or slot is provided", () => {
        const wrapper = mount(KsCodeStatus, {
            props: {status: "valid"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-code-status__text").exists()).toBe(true)
        expect(wrapper.find(".ks-code-status__text").text()).toBe("")
    })
})
